package org.example.restaurantsuggestionagent.reviews;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.example.restaurantsuggestionagent.discovery.PlaceReview;
import org.example.restaurantsuggestionagent.query.Budget;
import org.example.restaurantsuggestionagent.query.DietPreference;
import org.example.restaurantsuggestionagent.query.DietStrictness;
import org.example.restaurantsuggestionagent.query.Meal;
import org.example.restaurantsuggestionagent.query.TripQuery;
import org.junit.jupiter.api.Test;

class ContextReviewFinderTest {

    static TripQuery query(DietPreference diet, List<Meal> meals, List<String> avoid) {
        return new TripQuery("Barcelona", "", meals, diet, DietStrictness.UNSPECIFIED, Budget.UNSPECIFIED,
                List.of(), avoid, "", "en", 0, List.of());
    }

    static PlaceReview review(double rating, String text, String original) {
        return new PlaceReview(rating, text, original, "es", "Ana", "2026-09-01T10:00:00Z", "a month ago");
    }

    @Test
    void findsDietAndMealMentionsInTranslatedOrOriginalText() {
        PlaceReview veg = review(5, "Great options for vegans", "Muchas opciones para veganos");
        PlaceReview brunch = review(4, "Nice place", "El brunch es increíble");
        PlaceReview other = review(5, "Lovely staff", "Personal encantador");

        List<ContextReviewFinder.Match> matches = ContextReviewFinder.find(List.of(veg, brunch, other),
                query(DietPreference.VEGETARIAN, List.of(Meal.BREAKFAST), List.of()));

        assertThat(matches).extracting(ContextReviewFinder.Match::review).containsExactly(veg, brunch);
        assertThat(matches.get(0).mentions()).containsExactly("vegans", "veganos");
        assertThat(matches.get(1).mentions()).containsExactly("brunch");
    }

    @Test
    void accentsAndHyphensDontMatterButWholeWordsDo() {
        PlaceReview plantBased = review(5, "Fully Plant-Based menú", null);
        PlaceReview vegetable = review(4, "Lots of vegetables, Las Vegas vibes", null); // no whole-word match
        PlaceReview avoided = review(2, "Too many mushrooms everywhere", null);

        List<ContextReviewFinder.Match> matches = ContextReviewFinder.find(List.of(plantBased, vegetable, avoided),
                query(DietPreference.VEGETARIAN, List.of(), List.of("mushrooms")));

        assertThat(matches).extracting(ContextReviewFinder.Match::review).containsExactly(plantBased, avoided);
        assertThat(matches.get(1).mentions()).containsExactly("mushrooms");
    }

    @Test
    void anyDietSearchesOnlyForMealsCuisinesAndAvoidedFoods() {
        assertThat(ContextReviewFinder.terms(query(DietPreference.ANY, List.of(Meal.DINNER), List.of())))
                .contains("dinner", "cena", "sopar")
                .doesNotContain("vegan", "meat");
    }
}
