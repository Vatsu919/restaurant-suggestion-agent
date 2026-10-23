package org.example.restaurantsuggestionagent.answer;

import java.util.List;
import java.util.Set;

import org.example.restaurantsuggestionagent.query.Meal;

/**
 * One recommended restaurant: the facts, assembled in code from earlier steps, and the
 * LLM's write-up of them. Links come from the facts only, never from the LLM.
 *
 * @param calculation how the score was reached, e.g. "food match 100 × 29% = 28.6 (…) + … → 94.6"
 * @param dishes      dishes that suit the user, served at the recommended meals, with prices
 * @param writeUp     the LLM's text about this restaurant; null if it couldn't be written
 */
public record Recommendation(int rank, String placeId, String name, double score, String calculation,
                             Set<Meal> meals, String address, String websiteUri, String googleMapsUri,
                             String menuUrl, String menuLanguage, List<Dish> dishes, List<String> positiveReviews,
                             List<String> criticalReviews, List<String> reviewsAboutRequest,
                             RestaurantWriteUp writeUp) {

    /** A dish shown to the user: translated name, the name on the menu, and the price as written. */
    public record Dish(String name, String originalName, String price, String description) {
    }

    public Recommendation {
        meals = Set.copyOf(meals);
        dishes = List.copyOf(dishes);
        positiveReviews = List.copyOf(positiveReviews);
        criticalReviews = List.copyOf(criticalReviews);
        reviewsAboutRequest = List.copyOf(reviewsAboutRequest);
    }

    Recommendation withWriteUp(RestaurantWriteUp text) {
        return new Recommendation(rank, placeId, name, score, calculation, meals, address, websiteUri, googleMapsUri,
                menuUrl, menuLanguage, dishes, positiveReviews, criticalReviews, reviewsAboutRequest, text);
    }
}
