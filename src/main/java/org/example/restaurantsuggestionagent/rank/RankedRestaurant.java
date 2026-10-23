package org.example.restaurantsuggestionagent.rank;

import java.util.Set;

import org.example.restaurantsuggestionagent.query.Meal;

/**
 * A restaurant's place in the ranking, 1 being the best.
 *
 * @param meals the requested meals it is recommended for
 */
public record RankedRestaurant(String placeId, String name, int rank, Set<Meal> meals, ScoreBreakdown score) {

    public RankedRestaurant {
        meals = Set.copyOf(meals);
    }
}
