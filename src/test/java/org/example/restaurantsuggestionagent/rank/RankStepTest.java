package org.example.restaurantsuggestionagent.rank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.example.restaurantsuggestionagent.rank.RankTestData.DEFAULTS;
import static org.example.restaurantsuggestionagent.rank.RankTestData.menu;
import static org.example.restaurantsuggestionagent.rank.RankTestData.place;
import static org.example.restaurantsuggestionagent.rank.RankTestData.review;
import static org.example.restaurantsuggestionagent.rank.RankTestData.reviews;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.example.restaurantsuggestionagent.agent.RunContext;
import org.example.restaurantsuggestionagent.discovery.PlaceReview;
import org.example.restaurantsuggestionagent.events.AgentEvent;
import org.example.restaurantsuggestionagent.events.RunEventPublisher;
import org.example.restaurantsuggestionagent.query.Meal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RankStepTest {

    private static final String RECENT = "2026-09-01T10:00:00Z";

    private final RunEventPublisher events = new RunEventPublisher();
    private RunContext context;

    @BeforeEach
    void setUp() {
        UUID runId = UUID.randomUUID();
        events.open(runId);
        context = new RunContext(runId, "veg breakfast and dinner", events);
        context.query(RankTestData.vegetarian());
    }

    private static RankStep step() {
        return new RankStep(DEFAULTS, 2, new RestaurantScorer(3.0), Clock.fixed(Instant.parse("2026-10-24T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void ranksEveryoneInOneListWithReasons() {
        // b has more suitable dishes and good reviews about vegetarian food; a has a slightly better rating.
        PlaceReview veg = review(5, "Great vegetarian food", RECENT);
        context.candidates(List.of(place("a", 4.8, 2000, Set.of(Meal.DINNER)),
                place("b", 4.5, 2000, Set.of(Meal.BREAKFAST, Meal.DINNER))));
        context.menuReadings(Map.of("a", menu("a", 4, 12, 10), "b", menu("b", 20, 0, 10)));
        context.reviewInsights(Map.of("a", reviews("a", List.of(review(4, "Nice", RECENT)), List.of()),
                "b", reviews("b", List.of(veg, veg), List.of(veg, veg))));

        step().execute(context);

        assertThat(context.rankings()).extracting(RankedRestaurant::placeId).containsExactly("b", "a");
        assertThat(context.rankings().get(0).meals()).containsExactlyInAnyOrder(Meal.BREAKFAST, Meal.DINNER);
        List<String> messages = events.events(context.runId()).stream().map(AgentEvent::message).toList();
        assertThat(messages.get(0)).isEqualTo("How each restaurant is scored: "
                + "food match = 1 point per dish you can eat, max 100; dietary match = % of the menu you can eat; "
                + "rating = ★3 → 0, ★4 → 50, ★5 → 100; "
                + "positive reviews = % of the reviews Google shows that are ★4–5; "
                + "context reviews = 50, +10 per good and −10 per bad review about your request; "
                + "score = each mark × its weight, added up, minus 5 for each bad sign (a ★1–2 review, a critical "
                + "review mentioning hygiene, food safety, a diet mistake, rude staff or overcharging, Google saying "
                + "no vegetarian food, no review in the last year)");
        assertThat(messages.get(1)).startsWith("Weights for your request: FOOD_MATCH 25%")
                .endsWith("(PRICE_MATCH not used (no budget given))");
        // b: food 20×1=20, dietary 20/20=100, rating ★4.5=75, positive 2 of 2=100, context 50+2×10=70.
        // Weights without a budget: 20, 20, 10, 10, 20 out of 80 = 25%, 25%, 12.5%, 12.5%, 25%.
        assertThat(messages.get(2)).isEqualTo("#1 Name b: "
                + "food match 20 × 25% = 5.0 (20 dishes you can eat) "
                + "+ dietary match 100 × 25% = 25.0 (20 of 20 dishes suit vegetarian) "
                + "+ rating 75 × 13% = 9.4 (Google ★4.5 (2000 reviews)) "
                + "+ positive reviews 100 × 13% = 12.5 (2 of 2 reviews are positive) "
                + "+ context reviews 70 × 25% = 17.5 (2 good and 0 bad reviews about your request) "
                + "= 69.4; no bad signs → 69.4");
        assertThat(messages.get(3)).startsWith("#2 Name a: ");
        assertThat(messages.get(4)).startsWith("Ranking of 2 restaurants: 1. Name b (69.4); 2. Name a (");
    }
}
