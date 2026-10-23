package org.example.restaurantsuggestionagent.query;

import java.util.ArrayList;
import java.util.List;

/** What the user likes in a dish: the cuisines or dishes they named, plus how they like their food. */
public final class Preferences {

    private Preferences() {
    }

    /** E.g. ["tapas", "mild, not spicy"]; empty if the user named none. */
    public static List<String> liked(TripQuery query) {
        List<String> liked = new ArrayList<>(query.cuisinePreferences());
        liked.addAll(query.tastePreferences());
        return List.copyOf(liked);
    }
}
