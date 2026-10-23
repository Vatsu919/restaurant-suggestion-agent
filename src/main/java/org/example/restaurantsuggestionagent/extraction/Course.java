package org.example.restaurantsuggestionagent.extraction;

import org.example.restaurantsuggestionagent.query.Meal;

/** Which part of a meal a dish is; decides whether it counts as a real meal for the user. */
public enum Course {
    STARTER,
    MAIN,
    SIDE,
    DESSERT,
    /** Breakfast or brunch dishes: pancakes, toasts, bowls, pastries. */
    BREAKFAST,
    DRINK;

    /**
     * True if a dish of this course makes a meal at {@code meal}: never a drink; for lunch and
     * dinner, not a dessert (a place with only desserts that suit the user isn't a dinner option);
     * for breakfast, sweet dishes count.
     */
    public boolean makesAMealAt(Meal meal) {
        if (this == DRINK) {
            return false;
        }
        return meal == Meal.BREAKFAST || this != DESSERT;
    }
}
