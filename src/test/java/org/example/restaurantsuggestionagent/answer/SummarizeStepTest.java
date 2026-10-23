package org.example.restaurantsuggestionagent.answer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.example.restaurantsuggestionagent.agent.RunContext;
import org.example.restaurantsuggestionagent.agent.SkipReason;
import org.example.restaurantsuggestionagent.discovery.PlaceCandidate;
import org.example.restaurantsuggestionagent.discovery.PlaceReview;
import org.example.restaurantsuggestionagent.events.AgentEvent;
import org.example.restaurantsuggestionagent.events.RunEventPublisher;
import org.example.restaurantsuggestionagent.events.StepType;
import org.example.restaurantsuggestionagent.extraction.DietTag;
import org.example.restaurantsuggestionagent.extraction.EggContent;
import org.example.restaurantsuggestionagent.extraction.MenuItem;
import org.example.restaurantsuggestionagent.extraction.MenuKind;
import org.example.restaurantsuggestionagent.extraction.MenuReading;
import org.example.restaurantsuggestionagent.llm.LlmException;
import org.example.restaurantsuggestionagent.query.Budget;
import org.example.restaurantsuggestionagent.query.DietPreference;
import org.example.restaurantsuggestionagent.query.DietStrictness;
import org.example.restaurantsuggestionagent.query.Meal;
import org.example.restaurantsuggestionagent.query.TripQuery;
import org.example.restaurantsuggestionagent.rank.RankedRestaurant;
import org.example.restaurantsuggestionagent.rank.ScoreBreakdown;
import org.example.restaurantsuggestionagent.reviews.ContextReviewFinder;
import org.example.restaurantsuggestionagent.reviews.ReviewInsight;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SummarizeStepTest {

    private final RunEventPublisher events = new RunEventPublisher();
    private RunContext context;

    @BeforeEach
    void setUp() {
        UUID runId = UUID.randomUUID();
        events.open(runId);
        context = new RunContext(runId, "veg dinner", events);
        context.query(new TripQuery("Barcelona", "", List.of(Meal.DINNER), DietPreference.VEGETARIAN,
                DietStrictness.LACTO_OVO, Budget.UNSPECIFIED, List.of(), List.of(), "", "en", 0, List.of(), List.of()));
        // Three ranked restaurants and one skipped; only the top two are written up.
        context.candidates(List.of(place("a"), place("b"), place("c")));
        context.skip(StepType.FILTER, place("x"), SkipReason.LOW_RATING, "Rated 2.5, below the minimum of 3.0");
        context.rankings(List.of(ranked("a", 1, 90), ranked("b", 2, 70), ranked("c", 3, 50)));
        context.menuReadings(Map.of("a", menu("a"), "b", menu("b"), "c", menu("c")));
        context.reviewInsights(Map.of("a", reviews("a"), "b", reviews("b"), "c", reviews("c")));
    }

    @Test
    void writeUpsAreMatchedByIdAndLinksComeFromTheFacts() {
        AnswerWriter fake = (query, checked, recs, skipped) -> {
            assertThat(checked).isEqualTo(4);
            assertThat(recs).extracting(Recommendation::placeId).containsExactly("a", "b");
            assertThat(skipped).hasSize(1);
            return new AnswerText("I checked 4 restaurants.", List.of(writeUp("b"), writeUp("a")),
                    "One was rated too low.");
        };

        new SummarizeStep(fake, 2, 8).execute(context);

        FinalAnswer answer = context.answer();
        assertThat(answer.intro()).isEqualTo("I checked 4 restaurants.");
        assertThat(answer.recommendations()).extracting(r -> r.writeUp().headline())
                .containsExactly("Headline a", "Headline b");
        Recommendation a = answer.recommendations().get(0);
        assertThat(a.menuUrl()).isEqualTo("https://a.example/menu");
        assertThat(a.dishes()).extracting(Recommendation.Dish::price).containsExactly("8,50 €");
        assertThat(a.reviewsAboutRequest()).containsExactly("★5 \"Great vegetarian food\" (mentions vegetarian)");
        assertThat(answer.skipped()).extracting(s -> s.reason()).containsExactly(SkipReason.LOW_RATING);

        List<String> messages = events.events(context.runId()).stream()
                .filter(e -> e.step() == StepType.SUMMARIZE).map(AgentEvent::message).toList();
        assertThat(messages.get(0)).isEqualTo("""
                1. Name a (score 90.0, for dinner)
                Headline a
                Why: Why a
                What to order: Hummus 8,50 €
                Reviewers: Loved
                Menu: https://a.example/menu
                Website: https://a.example/
                Google Maps: https://maps.example/a""");
        assertThat(messages.get(2)).isEqualTo("I checked 4 restaurants.\n\nOne was rated too low.");
    }

    @Test
    void dishesMatchingThePreferencesComeFirst() {
        MenuItem mild = new MenuItem("Hummus", "Hummus", "", "", List.of(), "8 €", 8, DietTag.VEGAN,
                EggContent.NO_EGG, "", false);
        MenuItem salad = new MenuItem("Salad", "Ensalada", "", "", List.of(), "9 €", 9, DietTag.VEGAN,
                EggContent.NO_EGG, "", false);
        MenuItem spicy = new MenuItem("Curry picante", "Spicy curry", "", "", List.of(), "12 €", 12, DietTag.VEGAN,
                EggContent.NO_EGG, "", true);
        MenuReading menu = new MenuReading("a", "https://a.example/menu", MenuKind.REGULAR, "es", "EUR",
                List.of(mild, salad, spicy), List.of(mild, salad, spicy), false, false);

        Recommendation r = new SummarizeStep((q, c, recs, s) -> null, 2, 2)
                .facts(ranked("a", 1, 90), place("a"), menu, reviews("a"));

        // Only 2 dishes are shown: the spicy one first, then the first other dish on the menu.
        assertThat(r.dishes()).extracting(Recommendation.Dish::name).containsExactly("Spicy curry", "Hummus");
        assertThat(r.dishes().get(0).matchesPreferences()).isTrue();
    }

    @Test
    void ifTheWriteUpFailsTheFactsAreStillShown() {
        AnswerWriter failing = (query, checked, recs, skipped) -> {
            throw new LlmException("the model's answer was cut off before it finished");
        };

        new SummarizeStep(failing, 2, 8).execute(context);

        assertThat(context.answer().recommendations()).hasSize(2).allSatisfy(r -> assertThat(r.writeUp()).isNull());
        List<String> messages = events.events(context.runId()).stream()
                .filter(e -> e.step() == StepType.SUMMARIZE).map(AgentEvent::message).toList();
        assertThat(messages.get(0)).startsWith("The write-up couldn't be made");
        assertThat(messages.get(1)).contains("Dishes for you: Hummus 8,50 €", "Menu: https://a.example/menu");
    }

    private static RestaurantWriteUp writeUp(String id) {
        return new RestaurantWriteUp(id, "Headline " + id, "Why " + id, "Hummus 8,50 €", "Loved", "");
    }

    private static RankedRestaurant ranked(String id, int rank, double total) {
        return new RankedRestaurant(id, "Name " + id, rank, Set.of(Meal.DINNER),
                new ScoreBreakdown(List.of(), List.of(), total, 0, total));
    }

    private static MenuReading menu(String id) {
        MenuItem hummus = new MenuItem("Hummus", "Hummus", "Chickpeas", "Starters", List.of(), "8,50 €", 8.5,
                DietTag.VEGAN, EggContent.NO_EGG, "", false);
        return new MenuReading(id, "https://" + id + ".example/menu", MenuKind.REGULAR, "es", "EUR",
                List.of(hummus), List.of(hummus), false, false);
    }

    private static ReviewInsight reviews(String id) {
        PlaceReview good = new PlaceReview(5.0, "Great vegetarian food", null, "en", "X", null, null);
        return new ReviewInsight(id, 1, List.of(good), List.of(),
                List.of(new ContextReviewFinder.Match(good, List.of("vegetarian"))));
    }

    private static PlaceCandidate place(String id) {
        return new PlaceCandidate(id, "Name " + id, "Carrer " + id, "https://" + id + ".example/",
                "https://maps.example/" + id, 4.5, 100, null, "OPERATIONAL", null, null, null, null, null, null,
                List.of(), Set.of(Meal.DINNER), List.of());
    }
}
