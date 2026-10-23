package org.example.restaurantsuggestionagent.rank;

import java.util.ArrayList;
import java.util.List;

import org.example.restaurantsuggestionagent.discovery.PlaceReview;
import org.example.restaurantsuggestionagent.reviews.ContextReviewFinder;

/**
 * Finds serious problems named in critical reviews (3 stars or less), in English, Spanish
 * and Catalan. Only critical reviews are searched, so "not dirty at all" in a 5-star
 * review isn't counted. Each problem is counted once per restaurant.
 */
final class ReviewRedFlags {

    /** A kind of problem and words that name it (lower case, no accents). */
    enum Flag {
        HYGIENE("hygiene", List.of("dirty", "filthy", "unclean", "cockroach", "cockroaches", "rat", "rats",
                "hair in", "sucio", "sucia", "suciedad", "cucaracha", "cucarachas", "pelo en", "brut", "bruta")),
        FOOD_SAFETY("food safety", List.of("food poisoning", "got sick", "fell ill", "made me sick", "diarrhea",
                "vomiting", "raw chicken", "intoxicacion", "me puse malo", "nos pusimos malos", "intoxicacio")),
        DIET_MISTAKE("a diet mistake", List.of("had meat", "contained meat", "found meat", "with meat in",
                "not vegetarian", "not vegan", "wasn t vegan", "wasn t vegetarian", "ham in", "chicken in my",
                "llevaba carne", "tenia carne", "no era vegano", "no era vegetariano", "no es vegano")),
        RUDE_STAFF("rude staff", List.of("rude", "disrespectful", "ignored us", "unfriendly", "maleducado",
                "maleducada", "borde", "grosero", "grosera", "antipatico", "antipatica", "mal educat")),
        OVERCHARGING("overcharging", List.of("overcharged", "scam", "rip off", "ripoff", "charged us extra",
                "hidden charge", "hidden fee", "estafa", "timo", "nos cobraron de mas", "cobrar de mas"));

        final String label;
        final List<String> phrases;

        Flag(String label, List<String> phrases) {
            this.label = label;
            this.phrases = phrases;
        }
    }

    /** A problem found, with the review that names it. */
    record Found(Flag flag, PlaceReview review) {
    }

    private ReviewRedFlags() {
    }

    static List<Found> find(List<PlaceReview> criticalReviews) {
        List<Found> found = new ArrayList<>();
        for (Flag flag : Flag.values()) {
            for (PlaceReview review : criticalReviews) {
                String text = " " + ContextReviewFinder.normalize(review.text()) + " "
                        + ContextReviewFinder.normalize(review.originalText()) + " ";
                if (flag.phrases.stream().anyMatch(p -> text.contains(" " + p + " "))) {
                    found.add(new Found(flag, review));
                    break; // once per restaurant
                }
            }
        }
        return found;
    }
}
