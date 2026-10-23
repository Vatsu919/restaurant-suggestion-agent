package org.example.restaurantsuggestionagent.query;

public enum DietPreference {
    /** Only vegetarian dishes are shown. */
    VEGETARIAN,
    /** Meat and fish dishes are preferred; vegetarian dishes are not hidden. */
    NON_VEGETARIAN,
    /** Both are shown, each item labelled. Also used when the user does not say. */
    ANY
}
