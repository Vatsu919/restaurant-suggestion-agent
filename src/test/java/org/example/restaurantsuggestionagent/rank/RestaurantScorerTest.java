package org.example.restaurantsuggestionagent.rank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.example.restaurantsuggestionagent.rank.RankTestData.DEFAULTS;
import static org.example.restaurantsuggestionagent.rank.RankTestData.dish;
import static org.example.restaurantsuggestionagent.rank.RankTestData.menu;
import static org.example.restaurantsuggestionagent.rank.RankTestData.place;
import static org.example.restaurantsuggestionagent.rank.RankTestData.query;
import static org.example.restaurantsuggestionagent.rank.RankTestData.review;
import static org.example.restaurantsuggestionagent.rank.RankTestData.reviews;
import static org.example.restaurantsuggestionagent.rank.RankTestData.vegetarian;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import org.example.restaurantsuggestionagent.discovery.PlaceReview;
import org.example.restaurantsuggestionagent.extraction.DietTag;
import org.example.restaurantsuggestionagent.extraction.MenuItem;
import org.example.restaurantsuggestionagent.query.DietPreference;
import org.example.restaurantsuggestionagent.query.Meal;
import org.example.restaurantsuggestionagent.query.RankingFactor;
import org.junit.jupiter.api.Test;

class RestaurantScorerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 24);
    private static final String RECENT = "2026-09-01T10:00:00Z";

    @Test
    void foodMatchGivesFivePointsPerDishAndTenForTheKindAskedFor() {
        List<MenuItem> eight = IntStream.range(0, 8).mapToObj(i -> dish("D" + i, DietTag.VEGAN, 9, i < 4)).toList();
        List<MenuItem> thirty = IntStream.range(0, 30).mapToObj(i -> dish("D" + i, DietTag.VEGAN, 9, false)).toList();

        assertThat(RestaurantScorer.foodMatch(eight, vegetarian()).score()).isEqualTo(36); // 4×1 + 4×8
        assertThat(RestaurantScorer.foodMatch(thirty, vegetarian()).score()).isEqualTo(30);
        assertThat(RestaurantScorer.foodMatch(eight, query(DietPreference.VEGETARIAN, 0, List.of("tapas"), List.of()))
                .reason()).isEqualTo("8 dishes you can eat, 4 of them tapas");
    }

    @Test
    void priceMatchIsTheShareOfDishesWithinBudget() {
        List<MenuItem> dishes = List.of(dish("A", DietTag.VEGAN, 12, false), dish("B", DietTag.VEGAN, 20, false));

        RestaurantScorer.Dimension d = RestaurantScorer.priceMatch(dishes, place("p", 4.5, 100, Set.of(Meal.DINNER)),
                query(DietPreference.VEGETARIAN, 15, List.of(), List.of()));

        assertThat(d.score()).isEqualTo(50);
        assertThat(d.reason()).isEqualTo("1 of 2 dishes within 15");
    }

    @Test
    void ratingIsWhereItSitsBetweenTheFilterMinimumAndFiveStars() {
        assertThat(RestaurantScorer.rating(place("p", 4.0, 100, Set.of()), 4.0).score()).isEqualTo(0);
        assertThat(RestaurantScorer.rating(place("p", 4.6, 100, Set.of()), 4.0).score())
                .isCloseTo(60, org.assertj.core.api.Assertions.within(1e-9));
        assertThat(RestaurantScorer.rating(place("p", 5.0, 100, Set.of()), 4.0).score()).isEqualTo(100);
        // With a lower minimum, the same scale stretches: 3.5 → 0, 4.25 → 50.
        assertThat(RestaurantScorer.rating(place("p", 4.25, 100, Set.of()), 3.5).score())
                .isCloseTo(50, org.assertj.core.api.Assertions.within(1e-9));
    }

    @Test
    void positiveReviewsAreTheShareOfFourAndFiveStarReviews() {
        List<PlaceReview> five = List.of(review(5, "a", RECENT), review(4, "b", RECENT), review(5, "c", RECENT),
                review(5, "d", RECENT), review(2, "e", RECENT));

        RestaurantScorer.Dimension d = RestaurantScorer.positiveReviews(reviews("p", five, List.of()));

        assertThat(d.score()).isEqualTo(80);
        assertThat(d.reason()).isEqualTo("4 of 5 reviews are positive");
        assertThat(RestaurantScorer.positiveReviews(reviews("p", List.of(), List.of())).score()).isEqualTo(50);
    }

    @Test
    void contextReviewsStartAtFiftyAndMoveTenPerReview() {
        PlaceReview good = review(5, "Great vegetarian food", RECENT);
        PlaceReview bad = review(2, "Vegetarian options were poor", RECENT);

        assertThat(RestaurantScorer.contextReviews(reviews("p", List.of(), List.of())).score()).isEqualTo(50);
        assertThat(RestaurantScorer.contextReviews(reviews("p", List.of(good, good, good), List.of(good, good, good)))
                .score()).isEqualTo(80);
        assertThat(RestaurantScorer.contextReviews(reviews("p", List.of(good, bad), List.of(good, bad))).score())
                .isEqualTo(50);
    }

    @Test
    void everyBadSignCostsFivePoints() {
        List<Penalty> penalties = RestaurantScorer.penalties(place("p", 4.5, 100, Set.of(Meal.DINNER)),
                reviews("p", List.of(review(1, "Dirty tables and the vegan burger had meat in it", "2025-01-10T10:00:00Z"),
                        review(3, "Okay", "2025-02-01T10:00:00Z")), List.of()),
                vegetarian(), TODAY);

        assertThat(penalties).extracting(Penalty::reason).containsExactly(
                "a ★1 review",
                "a critical review mentions hygiene",
                "a critical review mentions a diet mistake",
                "no review in the last year (newest 2025-02-01)");
        assertThat(penalties).extracting(Penalty::points).containsOnly(5.0);
    }

    @Test
    void totalIsWeightedScoresMinusPenalties() {
        RankingWeights weights = RankingWeights.forRequest(DEFAULTS, 2, vegetarian());
        PlaceReview good = review(5, "Lovely vegetarian brunch", RECENT);

        ScoreBreakdown s = new RestaurantScorer(4.0).score(place("p", 4.6, 1000, Set.of(Meal.BREAKFAST, Meal.DINNER)),
                menu("p", 12, 4, 10), reviews("p", List.of(good, review(2, "Slow", RECENT)), List.of(good)),
                vegetarian(), weights, TODAY);

        assertThat(s.parts()).extracting(ScorePart::factor).doesNotContain(RankingFactor.PRICE_MATCH);
        assertThat(s.penalties()).extracting(Penalty::reason).containsExactly("a ★2 review");
        assertThat(s.total()).isEqualTo(Math.round((s.positive() - 5) * 10) / 10.0);
        assertThat(s.reasons()).contains("12 dishes you can eat", "12 of 16 dishes suit vegetarian",
                "1 of 2 reviews are positive",
                "Google ★4.6 (1000 reviews)", "1 good and 0 bad reviews about your request", "−5 a ★2 review");
    }
}
