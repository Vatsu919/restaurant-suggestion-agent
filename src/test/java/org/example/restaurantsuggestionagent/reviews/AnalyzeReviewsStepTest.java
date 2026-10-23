package org.example.restaurantsuggestionagent.reviews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.example.restaurantsuggestionagent.reviews.ContextReviewFinderTest.query;
import static org.example.restaurantsuggestionagent.reviews.ContextReviewFinderTest.review;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.example.restaurantsuggestionagent.agent.RunContext;
import org.example.restaurantsuggestionagent.discovery.PlaceCandidate;
import org.example.restaurantsuggestionagent.discovery.PlaceReview;
import org.example.restaurantsuggestionagent.events.AgentEvent;
import org.example.restaurantsuggestionagent.events.RunEventPublisher;
import org.example.restaurantsuggestionagent.query.DietPreference;
import org.example.restaurantsuggestionagent.query.Meal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AnalyzeReviewsStepTest {

    private final RunEventPublisher events = new RunEventPublisher();
    private RunContext context;

    @BeforeEach
    void setUp() {
        UUID runId = UUID.randomUUID();
        events.open(runId);
        context = new RunContext(runId, "veg dinner", events);
        context.query(query(DietPreference.VEGETARIAN, List.of(Meal.DINNER), List.of()));
    }

    @Test
    void sortsReviewsByStarsFindsRequestMentionsAndKeepsEveryRestaurant() {
        PlaceReview great = review(5, "Best vegan dinner in town", null);
        PlaceReview slow = review(2, "Very slow service", null);
        PlaceReview okay = review(3, "Fine, nothing special", null);
        context.candidates(List.of(place("a", List.of(great, slow, okay)), place("b", List.of())));

        new AnalyzeReviewsStep().execute(context);

        assertThat(context.candidates()).hasSize(2); // reviews never remove a restaurant
        ReviewInsight a = context.reviewInsights().get("a");
        assertThat(a.positive()).containsExactly(great);
        assertThat(a.critical()).containsExactly(slow, okay);
        assertThat(a.contextReviews()).extracting(ContextReviewFinder.Match::review).containsExactly(great);

        List<String> messages = events.events(context.runId()).stream().map(AgentEvent::message).toList();
        assertThat(messages).contains(
                "Name a: 3 Google reviews, 1 positive, 2 critical. Critical: ★2 \"Very slow service\". "
                        + "About your request (1 reviews): ★5 \"Best vegan dinner in town\" (mentions vegan, dinner)",
                "Name b: Google shows no reviews");
        assertThat(messages).last().asString()
                .isEqualTo("Read reviews for 2 restaurants: 1 have reviews about what you asked for, "
                        + "1 have critical reviews");
    }

    @Test
    void longReviewsAreCutAtAWordInMessages() {
        String longText = "The food was lovely ".repeat(20);

        assertThat(AnalyzeReviewsStep.quote(review(4, longText, null)))
                .startsWith("★4 \"The food was lovely")
                .endsWith("…\"")
                .hasSizeLessThan(160);
    }

    private static PlaceCandidate place(String id, List<PlaceReview> reviews) {
        return new PlaceCandidate(id, "Name " + id, null, "https://" + id + ".example/", null, 4.5, 100, null,
                "OPERATIONAL", null, null, null, null, null, null, reviews, Set.of(Meal.DINNER), List.of());
    }
}
