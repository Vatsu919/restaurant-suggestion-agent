package org.example.restaurantsuggestionagent.reviews;

import java.util.List;

import org.example.restaurantsuggestionagent.discovery.PlaceReview;

/**
 * One restaurant's Google reviews, grouped in code (no LLM).
 *
 * @param reviewsRead    reviews Google returned (at most 5, chosen by Google)
 * @param positive       reviews rated 4 or 5 stars
 * @param critical       reviews rated 3 stars or less
 * @param contextReviews reviews that mention what the user asked for
 */
public record ReviewInsight(String placeId, int reviewsRead, List<PlaceReview> positive, List<PlaceReview> critical,
                            List<ContextReviewFinder.Match> contextReviews) {

    public ReviewInsight {
        positive = List.copyOf(positive);
        critical = List.copyOf(critical);
        contextReviews = List.copyOf(contextReviews);
    }
}
