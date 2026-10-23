package org.example.restaurantsuggestionagent.reviews;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.example.restaurantsuggestionagent.discovery.PlaceReview;
import org.example.restaurantsuggestionagent.query.DietPreference;
import org.example.restaurantsuggestionagent.query.Meal;
import org.example.restaurantsuggestionagent.query.TripQuery;

/**
 * Finds, in code, the reviews that mention what this user asked for: their diet, meals,
 * cuisines and foods to avoid. Whole words are matched, in several languages, on both
 * the translated and the original review text.
 */
public final class ContextReviewFinder {

    /** A review and the words of the user's request it mentions. */
    public record Match(PlaceReview review, List<String> mentions) {
    }

    private static final List<String> VEGETARIAN_WORDS = List.of(
            "vegetarian", "vegetarians", "vegetariano", "vegetarianos", "vegetariana", "vegetarianas",
            "vegetaria", "vegetarien", "vegetarisch", "vegan", "vegans", "vegano", "veganos", "vegana", "veganas",
            "vega", "veggie", "plant based");

    private static final List<String> MEAT_FISH_WORDS = List.of(
            "meat", "carne", "carn", "steak", "fish", "pescado", "peix", "seafood", "marisco");

    private static final Map<Meal, List<String>> MEAL_WORDS = Map.of(
            Meal.BREAKFAST, List.of("breakfast", "brunch", "desayuno", "esmorzar", "petit dejeuner", "fruhstuck"),
            Meal.LUNCH, List.of("lunch", "almuerzo", "menu del dia", "dinar", "dejeuner", "mittagessen"),
            Meal.DINNER, List.of("dinner", "cena", "sopar", "diner", "abendessen"));

    private ContextReviewFinder() {
    }

    public static List<Match> find(List<PlaceReview> reviews, TripQuery query) {
        List<String> terms = terms(query);
        List<Match> matches = new ArrayList<>();
        for (PlaceReview review : reviews) {
            String text = " " + normalize(review.text()) + " " + normalize(review.originalText()) + " ";
            Set<String> mentions = new LinkedHashSet<>();
            for (String term : terms) {
                if (text.contains(" " + normalize(term) + " ")) {
                    mentions.add(term);
                }
            }
            if (!mentions.isEmpty()) {
                matches.add(new Match(review, List.copyOf(mentions)));
            }
        }
        return matches;
    }

    static List<String> terms(TripQuery query) {
        List<String> terms = new ArrayList<>();
        if (query.diet() == DietPreference.VEGETARIAN) {
            terms.addAll(VEGETARIAN_WORDS);
        } else if (query.diet() == DietPreference.NON_VEGETARIAN) {
            terms.addAll(MEAT_FISH_WORDS);
        }
        query.meals().forEach(meal -> terms.addAll(MEAL_WORDS.get(meal)));
        terms.addAll(query.cuisinePreferences());
        terms.addAll(query.avoid());
        return terms;
    }

    /** Lower case, no accents, words separated by single spaces: "Plant-Based Menú" → "plant based menu". */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").strip();
    }
}
