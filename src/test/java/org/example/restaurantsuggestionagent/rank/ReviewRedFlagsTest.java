package org.example.restaurantsuggestionagent.rank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.example.restaurantsuggestionagent.rank.RankTestData.review;

import java.util.List;

import org.example.restaurantsuggestionagent.discovery.PlaceReview;
import org.junit.jupiter.api.Test;

class ReviewRedFlagsTest {

    @Test
    void findsEachProblemOnceInEnglishAndSpanish() {
        PlaceReview spanish = new PlaceReview(2.0, null, "El camarero fue muy maleducado y nos cobraron de más",
                "es", "X", null, null);
        PlaceReview english = review(1, "Rude staff. Got sick after eating here.", null);

        List<ReviewRedFlags.Found> found = ReviewRedFlags.find(List.of(spanish, english));

        assertThat(found).extracting(ReviewRedFlags.Found::flag).containsExactly(
                ReviewRedFlags.Flag.FOOD_SAFETY, ReviewRedFlags.Flag.RUDE_STAFF, ReviewRedFlags.Flag.OVERCHARGING);
    }

    @Test
    void wordsInsideOtherWordsDontCount() {
        assertThat(ReviewRedFlags.find(List.of(review(3, "Prudent prices, a bit crowded", null)))).isEmpty();
    }
}
