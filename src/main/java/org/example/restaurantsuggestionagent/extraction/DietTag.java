package org.example.restaurantsuggestionagent.extraction;

/** What a dish contains, as far as the menu says. */
public enum DietTag {
    /** No animal products at all. */
    VEGAN,
    /** No meat or fish, but may contain dairy, egg or honey. */
    VEGETARIAN,
    /** Contains meat, fish or seafood. */
    MEAT_OR_FISH,
    /** The menu doesn't say enough to tell. */
    UNCLEAR
}
