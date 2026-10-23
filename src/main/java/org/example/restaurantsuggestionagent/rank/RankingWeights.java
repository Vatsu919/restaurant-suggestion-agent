package org.example.restaurantsuggestionagent.rank;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.example.restaurantsuggestionagent.query.Budget;
import org.example.restaurantsuggestionagent.query.DietPreference;
import org.example.restaurantsuggestionagent.query.RankingFactor;
import org.example.restaurantsuggestionagent.query.TripQuery;

/**
 * The weights used for one request, summing to 1, and why they differ from the defaults.
 * They are the same for every restaurant in a run, so scores stay comparable.
 *
 * @param notes e.g. "DIETARY_MATCH counts 2× (you said it matters most)"
 */
public record RankingWeights(Map<RankingFactor, Double> weights, List<String> notes) {

    public RankingWeights {
        weights = Collections.unmodifiableMap(new EnumMap<>(weights));
        notes = List.copyOf(notes);
    }

    public double of(RankingFactor factor) {
        return weights.getOrDefault(factor, 0.0);
    }

    /**
     * Starts from the defaults, drops factors the request gives nothing to judge by (no budget,
     * no diet), multiplies the user's priorities by {@code priorityBoost}, then scales the
     * weights to sum to 1.
     */
    public static RankingWeights forRequest(Map<RankingFactor, Double> defaults, double priorityBoost,
                                            TripQuery query) {
        Map<RankingFactor, Double> w = new EnumMap<>(defaults);
        List<String> notes = new ArrayList<>();

        if (query.maxSpendPerPerson() <= 0 && query.budget() == Budget.UNSPECIFIED) {
            w.put(RankingFactor.PRICE_MATCH, 0.0);
            notes.add("PRICE_MATCH not used (no budget given)");
        }
        if (query.diet() == DietPreference.ANY) {
            w.put(RankingFactor.DIETARY_MATCH, 0.0);
            notes.add("DIETARY_MATCH not used (no diet given, every dish suits)");
        }
        for (RankingFactor priority : query.priorities()) {
            if (w.get(priority) > 0) {
                w.put(priority, w.get(priority) * priorityBoost);
                notes.add(priority + " counts " + format(priorityBoost) + "× (you said it matters most)");
            }
        }

        double sum = w.values().stream().mapToDouble(Double::doubleValue).sum();
        w.replaceAll((factor, weight) -> weight / sum);
        return new RankingWeights(w, notes);
    }

    /** E.g. "DIETARY_MATCH 30%, FOOD_MATCH 20%, …", largest first. */
    public String describe() {
        return weights.entrySet().stream().filter(e -> e.getValue() > 0)
                .sorted(Map.Entry.<RankingFactor, Double>comparingByValue().reversed())
                .map(e -> e.getKey() + " " + Math.round(e.getValue() * 100) + "%")
                .reduce((a, b) -> a + ", " + b).orElse("");
    }

    private static String format(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }
}
