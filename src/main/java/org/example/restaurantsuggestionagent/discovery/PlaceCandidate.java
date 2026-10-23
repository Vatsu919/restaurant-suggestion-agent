package org.example.restaurantsuggestionagent.discovery;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.example.restaurantsuggestionagent.query.Meal;

/**
 * A restaurant found by Google Places, in our own format (independent of Google's JSON).
 *
 * <p>The {@code serves*} flags are {@link Boolean}, not {@code boolean}: Google often
 * doesn't know, and "unknown" (null) must stay different from "no" (false).
 *
 * @param priceLevel   Google's value, e.g. "PRICE_LEVEL_MODERATE"; null if unknown
 * @param matchedMeals meals this place is considered for; set by DISCOVER, narrowed by FILTER
 * @param notes        caveats shown with the restaurant, e.g. "Not open during breakfast hours"
 */
public record PlaceCandidate(
        String id,
        String name,
        String address,
        String websiteUri,
        String googleMapsUri,
        Double rating,
        Integer userRatingCount,
        String priceLevel,
        String businessStatus,
        String primaryType,
        Boolean servesBreakfast,
        Boolean servesLunch,
        Boolean servesDinner,
        Boolean servesVegetarianFood,
        OpeningHours openingHours,
        List<PlaceReview> reviews,
        Set<Meal> matchedMeals,
        List<String> notes) {

    public PlaceCandidate {
        reviews = reviews == null ? List.of() : List.copyOf(reviews);
        matchedMeals = matchedMeals == null || matchedMeals.isEmpty()
                ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(matchedMeals));
        notes = notes == null ? List.of() : List.copyOf(notes);
    }

    /** Google's answer to "does it serve this meal?": true, false, or null when unknown. */
    public Boolean serves(Meal meal) {
        return switch (meal) {
            case BREAKFAST -> servesBreakfast;
            case LUNCH -> servesLunch;
            case DINNER -> servesDinner;
        };
    }

    /** Returns a copy considered for exactly {@code meals}. */
    public PlaceCandidate withMeals(Set<Meal> meals) {
        return new PlaceCandidate(id, name, address, websiteUri, googleMapsUri, rating, userRatingCount,
                priceLevel, businessStatus, primaryType, servesBreakfast, servesLunch, servesDinner,
                servesVegetarianFood, openingHours, reviews, meals, notes);
    }

    /** Returns a copy with {@code more} appended to the notes. */
    public PlaceCandidate withNotes(Collection<String> more) {
        if (more.isEmpty()) {
            return this;
        }
        List<String> all = new ArrayList<>(notes);
        all.addAll(more);
        return new PlaceCandidate(id, name, address, websiteUri, googleMapsUri, rating, userRatingCount,
                priceLevel, businessStatus, primaryType, servesBreakfast, servesLunch, servesDinner,
                servesVegetarianFood, openingHours, reviews, matchedMeals, all);
    }
}
