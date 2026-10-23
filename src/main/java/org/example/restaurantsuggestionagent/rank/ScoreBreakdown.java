package org.example.restaurantsuggestionagent.rank;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * A restaurant's score and what it is made of, so the user can see why it ranks where it does.
 *
 * @param positive      sum of the parts' points, from 0 to 100
 * @param penaltyPoints points taken off
 * @param total         positive minus penalties, from 0 to 100
 */
public record ScoreBreakdown(List<ScorePart> parts, List<Penalty> penalties, double positive, double penaltyPoints,
                             double total) {

    public ScoreBreakdown {
        parts = List.copyOf(parts);
        penalties = List.copyOf(penalties);
    }

    /**
     * The whole calculation in one line, e.g. "food match 75 × 29% = 21.4 (15 dishes you can eat)
     * + rating 60 × 14% = 8.6 (Google ★4.2 (3000 reviews)) = 30.0; −5 a ★2 review → 25.0".
     */
    public String calculation() {
        String sum = parts.stream()
                .map(p -> String.format(Locale.ROOT, "%s %.0f × %d%% = %.1f (%s)", p.factor().label(), p.score(),
                        Math.round(p.weight() * 100), p.points(), p.reason()))
                .collect(Collectors.joining(" + "));
        String bad = penalties.isEmpty() ? "no bad signs"
                : penalties.stream().map(p -> String.format(Locale.ROOT, "−%.0f %s", p.points(), p.reason()))
                        .collect(Collectors.joining(", "));
        return String.format(Locale.ROOT, "%s = %.1f; %s → %.1f", sum, positive, bad, total);
    }

    /** E.g. "14 of 20 dishes suit vegetarian · Google ★4.6 (2100 reviews) · −5 a ★2 review". */
    public String reasons() {
        String good = parts.stream().map(ScorePart::reason).collect(Collectors.joining(" · "));
        String bad = penalties.stream()
                .map(p -> String.format(Locale.ROOT, "−%.0f %s", p.points(), p.reason()))
                .collect(Collectors.joining(" · "));
        return bad.isEmpty() ? good : good + " · " + bad;
    }
}
