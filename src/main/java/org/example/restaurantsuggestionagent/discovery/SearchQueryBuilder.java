package org.example.restaurantsuggestionagent.discovery;

import java.util.List;

import org.example.restaurantsuggestionagent.query.DietPreference;
import org.example.restaurantsuggestionagent.query.DietStrictness;
import org.example.restaurantsuggestionagent.query.Meal;
import org.example.restaurantsuggestionagent.query.TripQuery;

/** Builds the one free-text Google Places query for a request, covering all its meals. */
final class SearchQueryBuilder {

    private SearchQueryBuilder() {
    }

    /**
     * Examples: "vegetarian breakfast and dinner in Gràcia, Barcelona", "seafood dinner in Barcelona",
     * "restaurant for breakfast, lunch and dinner in Barcelona".
     */
    static String build(TripQuery query) {
        String where = query.neighbourhood().isEmpty()
                ? query.city()
                : query.neighbourhood() + ", " + query.city();
        String meals = mealWords(query.meals());

        StringBuilder food = new StringBuilder();
        if (query.diet() == DietPreference.VEGETARIAN) {
            food.append(query.strictness() == DietStrictness.VEGAN ? "vegan" : "vegetarian");
        }
        for (String cuisine : query.cuisinePreferences()) {
            food.append(food.isEmpty() ? "" : " ").append(cuisine);
        }

        return food.isEmpty()
                ? "restaurant for " + meals + " in " + where
                : food + " " + meals + " in " + where;
    }

    /** [BREAKFAST] → "breakfast"; [BREAKFAST, DINNER] → "breakfast and dinner"; all three → "breakfast, lunch and dinner". */
    static String mealWords(List<Meal> meals) {
        List<String> words = meals.stream().map(m -> m.name().toLowerCase()).toList();
        if (words.size() == 1) {
            return words.get(0);
        }
        return String.join(", ", words.subList(0, words.size() - 1)) + " and " + words.get(words.size() - 1);
    }
}
