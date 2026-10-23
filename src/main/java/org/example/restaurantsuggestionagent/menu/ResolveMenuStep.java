package org.example.restaurantsuggestionagent.menu;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import org.example.restaurantsuggestionagent.agent.PipelineStep;
import org.example.restaurantsuggestionagent.agent.RunContext;
import org.example.restaurantsuggestionagent.agent.SkipReason;
import org.example.restaurantsuggestionagent.discovery.PlaceCandidate;
import org.example.restaurantsuggestionagent.events.StepType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Step 4 of the pipeline: find and download each restaurant's menu. Restaurants are
 * checked in parallel; each result is reported as soon as it's known. Restaurants
 * without a menu are skipped with the reason and the URLs that were tried.
 */
@Component
@Order(40)
public class ResolveMenuStep implements PipelineStep {

    private static final Logger log = LoggerFactory.getLogger(ResolveMenuStep.class);

    /** How many tried URLs to quote in a skip message; the full list is in the event data. */
    private static final int ATTEMPTS_IN_MESSAGE = 5;

    private final MenuResolver resolver;
    private final Executor executor;

    public ResolveMenuStep(MenuResolver resolver, @Qualifier("menuExecutor") Executor executor) {
        this.resolver = resolver;
        this.executor = executor;
    }

    @Override
    public StepType type() {
        return StepType.RESOLVE_MENU;
    }

    @Override
    public String description() {
        return "Looking for each restaurant's menu on its website";
    }

    @Override
    public void execute(RunContext context) {
        List<PlaceCandidate> candidates = context.candidates();
        Map<String, MenuSource> menus = new ConcurrentHashMap<>();
        Map<String, SkipReason> skips = new ConcurrentHashMap<>();

        CompletableFuture.allOf(candidates.stream()
                .map(place -> CompletableFuture.runAsync(() -> handle(context, place, menus, skips), executor))
                .toArray(CompletableFuture[]::new)).join();

        List<PlaceCandidate> withMenu = candidates.stream().filter(p -> menus.containsKey(p.id())).toList();
        if (withMenu.isEmpty()) {
            throw new IllegalStateException("No menu could be found for any of the " + candidates.size()
                    + " restaurants " + countByReason(skips));
        }
        context.candidates(withMenu);
        context.menus(menus);

        Map<MenuType, Integer> byType = new EnumMap<>(MenuType.class);
        menus.values().forEach(m -> byType.merge(m.type(), 1, Integer::sum));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("withMenu", withMenu.size());
        data.put("menuTypes", byType);
        data.put("skippedByReason", countByReason(skips));
        context.progress(type(), "Found menus for " + withMenu.size() + " of " + candidates.size()
                + " restaurants " + byType
                + (skips.isEmpty() ? "" : "; skipped " + skips.size() + " " + countByReason(skips)), data);
    }

    private void handle(RunContext context, PlaceCandidate place,
                        Map<String, MenuSource> menus, Map<String, SkipReason> skips) {
        MenuResolution result;
        try {
            result = resolver.resolve(place);
        } catch (RuntimeException e) {
            // One broken website must never fail the whole run.
            log.warn("Menu lookup failed for {} ({})", place.name(), place.websiteUri(), e);
            result = MenuResolution.notFound(SkipReason.MENU_NOT_FOUND,
                    "Unexpected error while reading the website", List.of());
        }

        if (result.isFound()) {
            MenuSource menu = result.source();
            menus.put(place.id(), menu);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("menuUrl", menu.url());
            data.put("menuType", menu.type());
            data.put("sizeKb", menu.content().length / 1024);
            data.put("foundVia", menu.foundVia());
            data.put("attempts", result.attempts());
            context.progress(type(), place.id(), place.name() + ": menu found (" + menu.type() + ") via "
                    + menu.foundVia() + " → " + menu.url(), data);
        } else {
            skips.put(place.id(), result.reason());
            context.skip(type(), place, result.reason(), withAttempts(result), Map.of("attempts", result.attempts()));
        }
    }

    private static String withAttempts(MenuResolution result) {
        List<String> attempts = result.attempts();
        if (attempts.isEmpty()) {
            return result.detail();
        }
        String shown = String.join(", ", attempts.subList(0, Math.min(ATTEMPTS_IN_MESSAGE, attempts.size())));
        String more = attempts.size() > ATTEMPTS_IN_MESSAGE ? " and " + (attempts.size() - ATTEMPTS_IN_MESSAGE) + " more" : "";
        return result.detail() + " (checked: " + shown + more + ")";
    }

    private static Map<SkipReason, Integer> countByReason(Map<String, SkipReason> skips) {
        Map<SkipReason, Integer> counts = new EnumMap<>(SkipReason.class);
        skips.values().forEach(r -> counts.merge(r, 1, Integer::sum));
        return counts;
    }
}
