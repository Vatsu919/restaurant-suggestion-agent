package org.example.restaurantsuggestionagent.extraction;

import org.example.restaurantsuggestionagent.query.DietPreference;
import org.example.restaurantsuggestionagent.query.DietStrictness;
import org.example.restaurantsuggestionagent.query.TripQuery;

/**
 * Decides in code, not in the LLM, whether a dish suits the user. Dishes tagged
 * UNCLEAR never count as vegetarian: a wrong "yes" is worse than a missed dish.
 */
public final class DietMatcher {

    private DietMatcher() {
    }

    public static boolean matches(MenuItem item, TripQuery query) {
        if (!item.containsAvoided().isBlank()) {
            return false;
        }
        return switch (query.diet()) {
            case VEGETARIAN -> suitsVegetarian(item, query.strictness());
            case NON_VEGETARIAN -> item.diet() == DietTag.MEAT_OR_FISH;
            case ANY -> true;
        };
    }

    private static boolean suitsVegetarian(MenuItem item, DietStrictness strictness) {
        return switch (strictness) {
            case VEGAN -> item.diet() == DietTag.VEGAN;
            case NO_EGG -> item.diet() == DietTag.VEGAN
                    || (item.diet() == DietTag.VEGETARIAN && item.egg() == EggContent.NO_EGG);
            case LACTO_OVO, UNSPECIFIED -> item.diet() == DietTag.VEGAN || item.diet() == DietTag.VEGETARIAN;
        };
    }

    /** How the user's diet is described in messages, e.g. "vegan", "vegetarian (no egg)". */
    public static String describe(TripQuery query) {
        if (query.diet() == DietPreference.NON_VEGETARIAN) {
            return "meat or fish";
        }
        if (query.diet() == DietPreference.ANY) {
            return "any diet";
        }
        return switch (query.strictness()) {
            case VEGAN -> "vegan";
            case NO_EGG -> "vegetarian (no egg)";
            case LACTO_OVO, UNSPECIFIED -> "vegetarian";
        };
    }
}
