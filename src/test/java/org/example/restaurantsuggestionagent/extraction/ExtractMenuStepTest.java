package org.example.restaurantsuggestionagent.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.example.restaurantsuggestionagent.extraction.TestData.dish;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.example.restaurantsuggestionagent.agent.RunContext;
import org.example.restaurantsuggestionagent.agent.SkipReason;
import org.example.restaurantsuggestionagent.agent.SkippedRestaurant;
import org.example.restaurantsuggestionagent.discovery.PlaceCandidate;
import org.example.restaurantsuggestionagent.events.AgentEvent;
import org.example.restaurantsuggestionagent.events.RunEventPublisher;
import org.example.restaurantsuggestionagent.llm.LlmException;
import org.example.restaurantsuggestionagent.menu.MenuSource;
import org.example.restaurantsuggestionagent.menu.MenuType;
import org.example.restaurantsuggestionagent.query.Meal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExtractMenuStepTest {

    private final RunEventPublisher events = new RunEventPublisher();
    /** What the fake LLM answers, by restaurant name; no answer means the LLM call fails. */
    private final Map<String, MenuExtraction> answers = new HashMap<>();
    /** What the fake LLM was given, by restaurant name. */
    private final Map<String, MenuInput> inputsSeen = new ConcurrentHashMap<>();
    private RunContext context;

    @BeforeEach
    void setUp() {
        UUID runId = UUID.randomUUID();
        events.open(runId);
        context = new RunContext(runId, "veg dinner", events);
        context.query(TestData.vegetarian());
    }

    private ExtractMenuStep step(int maxChars) {
        MenuExtractor fake = (name, menu, query) -> {
            inputsSeen.put(name, menu);
            MenuExtraction answer = answers.get(name);
            if (answer == null) {
                throw new LlmException("the model's answer was cut off before it finished");
            }
            return answer;
        };
        return new ExtractMenuStep(fake, Runnable::run, maxChars);
    }

    @Test
    void keepsRestaurantsWithMatchingDishesAndSkipsTheRestWithReasons() {
        menus("a", "b", "c", "d", "e");
        answers.put("Name a", regular(dish("Hummus", DietTag.VEGAN, EggContent.NO_EGG),
                dish("Jamón", DietTag.MEAT_OR_FISH, EggContent.NO_EGG)));
        answers.put("Name b", new MenuExtraction(MenuKind.GROUP_OR_EVENT, "A set menu for groups of 10 or more",
                "es", "EUR", List.of(dish("Paella", DietTag.MEAT_OR_FISH, EggContent.NO_EGG)), false));
        answers.put("Name c", regular(dish("Jamón", DietTag.MEAT_OR_FISH, EggContent.NO_EGG),
                dish("Croquetas", DietTag.UNCLEAR, EggContent.UNKNOWN)));
        answers.put("Name d", regular());

        step(40000).execute(context);

        assertThat(context.candidates()).extracting(PlaceCandidate::id).containsExactly("a");
        MenuReading reading = context.menuReadings().get("a");
        assertThat(reading.items()).hasSize(2);
        assertThat(reading.matchingItems()).extracting(MenuItem::originalName).containsExactly("Hummus");
        assertThat(reading.menuUrl()).isEqualTo("https://a.example/carta");

        assertThat(context.skippedRestaurants()).extracting(SkippedRestaurant::id, SkippedRestaurant::reason,
                SkippedRestaurant::detail).containsExactly(
                tuple("b", SkipReason.NOT_REGULAR_MENU,
                        "The menu found isn't the regular menu: A set menu for groups of 10 or more"),
                tuple("c", SkipReason.NO_MATCHING_ITEMS,
                        "None of the 2 dishes suits vegetarian (1 don't say enough to tell)"),
                tuple("d", SkipReason.NO_MENU_ITEMS, "No dishes could be read from the menu"),
                tuple("e", SkipReason.MENU_UNREADABLE,
                        "The menu couldn't be read (the model's answer was cut off before it finished)"));

        List<String> messages = events.events(context.runId()).stream().map(AgentEvent::message).toList();
        assertThat(messages).contains("Name a: Spanish menu, 2 dishes read, 1 suit vegetarian, e.g. Hummus 9,50 € "
                + "→ https://a.example/carta");
        assertThat(messages).last().asString().startsWith(
                "Read 5 menus: 1 restaurants have 1 dishes that suit vegetarian; skipped 4");
    }

    @Test
    void onlyTextIsSentAndLongMenusAreCutToTheLimit() {
        context.candidates(List.of(place("a")));
        String html = "<html><head><script>var tracking = 1;</script></head><body><nav>Home | Menu</nav>"
                + "<h2>Mains</h2><p>Hummus 8,50 €</p><p>" + "Falafel 9,00 € ".repeat(20) + "</p></body></html>";
        context.menus(Map.of("a", new MenuSource("https://a.example/carta", MenuType.HTML, "text/html",
                html.getBytes(StandardCharsets.UTF_8), "test")));
        answers.put("Name a", regular(dish("Hummus", DietTag.VEGAN, EggContent.NO_EGG)));

        step(30).execute(context);

        assertThat(inputsSeen.get("Name a").text()).isEqualTo("Mains\nHummus 8,50 €\nFalafel 9,00 € Falafel".substring(0, 30))
                .doesNotContain("tracking", "Home");
        assertThat(context.menuReadings().get("a").textTruncated()).isTrue();
    }

    @Test
    void pdfMenusAreSentAsThePdfItself() {
        context.candidates(List.of(place("a")));
        byte[] pdf = "%PDF-1.7 menu".getBytes(StandardCharsets.US_ASCII);
        context.menus(Map.of("a", new MenuSource("https://a.example/carta.pdf", MenuType.PDF, "application/pdf",
                pdf, "test")));
        answers.put("Name a", regular(dish("Hummus", DietTag.VEGAN, EggContent.NO_EGG)));

        step(40000).execute(context);

        assertThat(inputsSeen.get("Name a").isPdf()).isTrue();
        assertThat(inputsSeen.get("Name a").pdf()).isEqualTo(pdf);
    }

    @Test
    void mealsWithoutSuitableDishesAreDroppedAndAllDroppedMeansSkipped() {
        menus("a", "b");
        context.candidates(List.of(place("a", Set.of(Meal.BREAKFAST, Meal.DINNER)),
                place("b", Set.of(Meal.BREAKFAST))));
        // a: the vegan dish is dinner only, the all-day dish is meat. b: only a dinner dish suits.
        answers.put("Name a", regular(TestData.dish("Curry", DietTag.VEGAN, EggContent.NO_EGG, List.of(Meal.DINNER)),
                dish("Jamón", DietTag.MEAT_OR_FISH, EggContent.NO_EGG)));
        answers.put("Name b", regular(TestData.dish("Risotto", DietTag.VEGETARIAN, EggContent.NO_EGG,
                List.of(Meal.DINNER))));

        step(40000).execute(context);

        assertThat(context.candidates()).extracting(PlaceCandidate::id).containsExactly("a");
        assertThat(context.candidates().get(0).matchedMeals()).containsExactly(Meal.DINNER);
        assertThat(context.menuReadings().get("a").matchingFor(Meal.DINNER)).hasSize(1);
        assertThat(context.skippedRestaurants()).extracting(SkippedRestaurant::id, SkippedRestaurant::detail)
                .containsExactly(tuple("b", "None of the 1 dishes that suit vegetarian is served at breakfast"));
        List<String> messages = events.events(context.runId()).stream().map(AgentEvent::message).toList();
        assertThat(messages).anySatisfy(msg -> assertThat(msg).startsWith("Name a: ")
                .endsWith("(not considered for breakfast: no suitable dishes served then)"));
    }

    @Test
    void noMatchingDishesAnywhereFailsTheRun() {
        menus("a");
        answers.put("Name a", regular(dish("Jamón", DietTag.MEAT_OR_FISH, EggContent.NO_EGG)));

        assertThatThrownBy(() -> step(40000).execute(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("None of the 1 menus had dishes that suit vegetarian");
    }

    private void menus(String... ids) {
        Map<String, MenuSource> menus = new HashMap<>();
        for (String id : ids) {
            menus.put(id, new MenuSource("https://" + id + ".example/carta", MenuType.HTML, "text/html",
                    "<p>Hummus 8,50 €</p>".getBytes(StandardCharsets.UTF_8), "test"));
        }
        context.menus(menus);
        context.candidates(Arrays.stream(ids).map(ExtractMenuStepTest::place).toList());
    }

    private static MenuExtraction regular(MenuItem... items) {
        return new MenuExtraction(MenuKind.REGULAR, "The restaurant's food menu", "es", "EUR", List.of(items), false);
    }

    private static PlaceCandidate place(String id) {
        return place(id, Set.of(Meal.DINNER));
    }

    private static PlaceCandidate place(String id, Set<Meal> meals) {
        return new PlaceCandidate(id, "Name " + id, null, "https://" + id + ".example/", null, 4.5, 100, null,
                "OPERATIONAL", null, null, null, null, null, null, List.of(), meals, List.of());
    }
}
