package org.example.restaurantsuggestionagent.extraction;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import org.example.restaurantsuggestionagent.agent.PipelineStep;
import org.example.restaurantsuggestionagent.agent.RunContext;
import org.example.restaurantsuggestionagent.agent.SkipReason;
import org.example.restaurantsuggestionagent.discovery.PlaceCandidate;
import org.example.restaurantsuggestionagent.events.StepType;
import org.example.restaurantsuggestionagent.menu.MenuSource;
import org.example.restaurantsuggestionagent.menu.MenuTextExtractor;
import org.example.restaurantsuggestionagent.menu.MenuType;
import org.example.restaurantsuggestionagent.query.DietPreference;
import org.example.restaurantsuggestionagent.query.Meal;
import org.example.restaurantsuggestionagent.query.TripQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Step 5 of the pipeline: read each downloaded menu. HTML menus are turned into text in
 * Java (Jsoup); PDF menus are sent to the LLM as they are, because their layout matters.
 * The LLM turns the menu into dishes with prices, translations and diet tags; Java then
 * keeps the dishes that suit the user. A meal is dropped for a restaurant when none of
 * its suitable dishes is served at that meal. Restaurants are skipped when the menu
 * isn't the regular one, has no dishes, or has nothing for the user's diet and meals.
 */
@Component
@Order(50)
public class ExtractMenuStep implements PipelineStep {

    private static final Logger log = LoggerFactory.getLogger(ExtractMenuStep.class);

    /** Dishes quoted in a restaurant's progress message; all matching dishes are in the event data. */
    private static final int EXAMPLES_IN_MESSAGE = 3;

    private final MenuExtractor extractor;
    private final Executor executor;
    private final int maxMenuChars;

    public ExtractMenuStep(MenuExtractor extractor, @Qualifier("llmExecutor") Executor executor,
                           @Value("${extract.max-menu-chars:40000}") int maxMenuChars) {
        this.extractor = extractor;
        this.executor = executor;
        this.maxMenuChars = maxMenuChars;
    }

    @Override
    public StepType type() {
        return StepType.EXTRACT_MENU;
    }

    @Override
    public String description() {
        return "Reading and translating each menu, and finding the dishes that suit you";
    }

    @Override
    public void execute(RunContext context) {
        List<PlaceCandidate> candidates = context.candidates();
        Map<String, MenuReading> readings = new ConcurrentHashMap<>();
        Map<String, SkipReason> skips = new ConcurrentHashMap<>();
        Map<String, Set<Meal>> mealsLeft = new ConcurrentHashMap<>();

        CompletableFuture.allOf(candidates.stream()
                .map(place -> CompletableFuture.runAsync(
                        () -> handle(context, place, readings, skips, mealsLeft), executor))
                .toArray(CompletableFuture[]::new)).join();

        List<PlaceCandidate> kept = candidates.stream().filter(p -> readings.containsKey(p.id()))
                .map(p -> p.withMeals(mealsLeft.get(p.id()))).toList();
        if (kept.isEmpty()) {
            throw new IllegalStateException("None of the " + candidates.size() + " menus had dishes that suit "
                    + DietMatcher.describe(context.query()) + " " + countByReason(skips));
        }
        context.candidates(kept);
        context.menuReadings(readings);

        int dishes = readings.values().stream().mapToInt(r -> r.matchingItems().size()).sum();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("kept", kept.size());
        data.put("matchingDishes", dishes);
        data.put("skippedByReason", countByReason(skips));
        context.progress(type(), "Read " + candidates.size() + " menus: " + kept.size() + " restaurants have "
                + dishes + " dishes that suit " + DietMatcher.describe(context.query())
                + (skips.isEmpty() ? "" : "; skipped " + skips.size() + " " + countByReason(skips)), data);
    }

    private void handle(RunContext context, PlaceCandidate place, Map<String, MenuReading> readings,
                        Map<String, SkipReason> skips, Map<String, Set<Meal>> mealsLeft) {
        TripQuery query = context.query();
        MenuSource menu = context.menus().get(place.id());

        MenuInput input;
        boolean truncated = false;
        if (menu.type() == MenuType.PDF) {
            input = MenuInput.ofPdf(menu.content());
        } else {
            String text;
            try {
                text = MenuTextExtractor.htmlText(menu);
            } catch (IOException | RuntimeException e) {
                skip(context, place, skips, SkipReason.MENU_UNREADABLE, "The menu page couldn't be read", menu);
                return;
            }
            if (text.isBlank()) {
                skip(context, place, skips, SkipReason.MENU_UNREADABLE, "The menu page has no readable text", menu);
                return;
            }
            truncated = text.length() > maxMenuChars;
            input = MenuInput.ofText(truncated ? text.substring(0, maxMenuChars) : text);
        }

        MenuExtraction extraction;
        try {
            extraction = extractor.extract(place.name(), input, query);
        } catch (RuntimeException e) {
            // One unreadable menu must never fail the whole run.
            log.warn("Menu extraction failed for {} ({})", place.name(), menu.url(), e);
            skip(context, place, skips, SkipReason.MENU_UNREADABLE,
                    "The menu couldn't be read (" + e.getMessage() + ")", menu);
            return;
        }

        if (extraction.menuKind() != MenuKind.REGULAR) {
            skip(context, place, skips, SkipReason.NOT_REGULAR_MENU,
                    "The menu found isn't the regular menu: " + extraction.menuKindReason(), menu);
            return;
        }
        List<MenuItem> items = extraction.items();
        if (items.isEmpty()) {
            skip(context, place, skips, SkipReason.NO_MENU_ITEMS, "No dishes could be read from the menu", menu);
            return;
        }
        List<MenuItem> matching = items.stream().filter(item -> DietMatcher.matches(item, query)).toList();
        if (matching.isEmpty()) {
            skip(context, place, skips, SkipReason.NO_MATCHING_ITEMS, noMatchDetail(items, query), menu);
            return;
        }
        Set<Meal> meals = EnumSet.noneOf(Meal.class);
        List<String> dropped = new ArrayList<>();
        for (Meal meal : place.matchedMeals()) {
            if (matching.stream().anyMatch(i -> i.servedAt(meal))) {
                meals.add(meal);
            } else {
                dropped.add(meal.name().toLowerCase(Locale.ROOT));
            }
        }
        if (meals.isEmpty()) {
            skip(context, place, skips, SkipReason.NO_MATCHING_ITEMS, "None of the " + matching.size()
                    + " dishes that suit " + DietMatcher.describe(query) + " is served at "
                    + String.join(" or ", dropped), menu);
            return;
        }
        mealsLeft.put(place.id(), meals);

        MenuReading reading = new MenuReading(place.id(), menu.url(), extraction.menuKind(),
                extraction.menuLanguage(), extraction.currency(), items, matching, extraction.itemsOmitted(), truncated);
        readings.put(place.id(), reading);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("menuUrl", menu.url());
        data.put("menuLanguage", extraction.menuLanguage());
        data.put("currency", extraction.currency());
        data.put("dishCount", items.size());
        data.put("matchingCount", matching.size());
        data.put("matchingDishes", matching.stream().map(ExtractMenuStep::dishView).toList());
        data.put("itemsOmitted", extraction.itemsOmitted());
        data.put("sentAs", input.isPdf() ? "PDF" : "text");
        data.put("sizeKb", (input.isPdf() ? input.pdf().length : input.text().length()) / 1024);
        data.put("textTruncated", truncated);
        data.put("droppedMeals", dropped);
        context.progress(type(), place.id(), place.name() + ": " + languageName(extraction.menuLanguage())
                + " menu, " + items.size() + " dishes read" + (extraction.itemsOmitted() ? " (more not listed)" : "")
                + ", " + matching.size() + " suit " + DietMatcher.describe(query) + ", e.g. " + examples(matching)
                + " → " + menu.url()
                + (dropped.isEmpty() ? "" : " (not considered for " + String.join(" or ", dropped)
                        + ": no suitable dishes served then)"), data);
    }

    private static void skip(RunContext context, PlaceCandidate place, Map<String, SkipReason> skips,
                             SkipReason reason, String detail, MenuSource menu) {
        skips.put(place.id(), reason);
        context.skip(StepType.EXTRACT_MENU, place, reason, detail, Map.of("menuUrl", menu.url()));
    }

    /** E.g. "None of the 24 dishes suits vegetarian (7 don't say enough to tell)". */
    private static String noMatchDetail(List<MenuItem> items, TripQuery query) {
        String detail = "None of the " + items.size() + " dishes suits " + DietMatcher.describe(query);
        long unclear = items.stream().filter(i -> i.diet() == DietTag.UNCLEAR).count();
        if (query.diet() == DietPreference.VEGETARIAN && unclear > 0) {
            detail += " (" + unclear + " don't say enough to tell)";
        }
        long avoided = items.stream().filter(i -> !i.containsAvoided().isBlank()).count();
        if (avoided > 0) {
            detail += " (" + avoided + " contain foods you avoid)";
        }
        return detail;
    }

    private static String examples(List<MenuItem> matching) {
        return matching.stream().limit(EXAMPLES_IN_MESSAGE)
                .map(i -> i.translatedName() + (i.priceText().isBlank() ? "" : " " + i.priceText()))
                .collect(Collectors.joining("; "));
    }

    private static Map<String, Object> dishView(MenuItem item) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("name", item.translatedName());
        view.put("originalName", item.originalName());
        view.put("description", item.description());
        view.put("section", item.section());
        view.put("meals", item.meals());
        view.put("price", item.priceText());
        view.put("diet", item.diet());
        return view;
    }

    /** "es" → "Spanish"; unknown codes are shown as they are. */
    private static String languageName(String code) {
        String name = Locale.forLanguageTag(code == null ? "" : code).getDisplayLanguage(Locale.ENGLISH);
        return name.isBlank() ? String.valueOf(code) : name;
    }

    private static Map<SkipReason, Integer> countByReason(Map<String, SkipReason> skips) {
        Map<SkipReason, Integer> counts = new EnumMap<>(SkipReason.class);
        skips.values().forEach(r -> counts.merge(r, 1, Integer::sum));
        return counts;
    }
}
