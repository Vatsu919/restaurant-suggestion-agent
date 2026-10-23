package org.example.restaurantsuggestionagent.rank;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.example.restaurantsuggestionagent.agent.PipelineStep;
import org.example.restaurantsuggestionagent.agent.RunContext;
import org.example.restaurantsuggestionagent.discovery.PlaceCandidate;
import org.example.restaurantsuggestionagent.events.StepType;
import org.example.restaurantsuggestionagent.query.RankingFactor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Step 7 of the pipeline: rank all remaining restaurants in one list. The weights start
 * from the configured defaults and adapt to the request (what the user says matters most,
 * whether a budget or diet was given). Pure code, no LLM; every score comes with its reasons.
 */
@Component
@Order(70)
public class RankStep implements PipelineStep {

    private final Map<RankingFactor, Double> defaultWeights;
    private final double priorityBoost;
    private final RestaurantScorer scorer;
    private final Clock clock;

    @Autowired
    public RankStep(@Value("${rank.weight.food-match:20}") double foodMatch,
                    @Value("${rank.weight.dietary-match:20}") double dietaryMatch,
                    @Value("${rank.weight.price-match:15}") double priceMatch,
                    @Value("${rank.weight.rating:10}") double rating,
                    @Value("${rank.weight.positive-reviews:10}") double positiveReviews,
                    @Value("${rank.weight.context-reviews:20}") double contextReviews,
                    @Value("${rank.priority-boost:2}") double priorityBoost,
                    @Value("${filter.min-rating:3.0}") double minRating) {
        this(weights(foodMatch, dietaryMatch, priceMatch, rating, positiveReviews, contextReviews),
                priorityBoost, new RestaurantScorer(minRating), Clock.systemDefaultZone());
    }

    RankStep(Map<RankingFactor, Double> defaultWeights, double priorityBoost, RestaurantScorer scorer, Clock clock) {
        this.defaultWeights = new EnumMap<>(defaultWeights);
        this.priorityBoost = priorityBoost;
        this.scorer = scorer;
        this.clock = clock;
    }

    static Map<RankingFactor, Double> weights(double foodMatch, double dietaryMatch, double priceMatch, double rating,
                                              double positiveReviews, double contextReviews) {
        Map<RankingFactor, Double> w = new EnumMap<>(RankingFactor.class);
        w.put(RankingFactor.FOOD_MATCH, foodMatch);
        w.put(RankingFactor.DIETARY_MATCH, dietaryMatch);
        w.put(RankingFactor.PRICE_MATCH, priceMatch);
        w.put(RankingFactor.RATING, rating);
        w.put(RankingFactor.POSITIVE_REVIEWS, positiveReviews);
        w.put(RankingFactor.CONTEXT_REVIEWS, contextReviews);
        return w;
    }

    @Override
    public StepType type() {
        return StepType.RANK;
    }

    @Override
    public String description() {
        return "Ranking the restaurants";
    }

    @Override
    public void execute(RunContext context) {
        RankingWeights weights = RankingWeights.forRequest(defaultWeights, priorityBoost, context.query());
        List<String> rules = scorer.rules(weights, context.query());
        context.progress(type(), "How each restaurant is scored: " + String.join("; ", rules),
                Map.of("rules", rules));
        Map<String, Object> weightData = new LinkedHashMap<>();
        weightData.put("weights", weights.weights());
        weightData.put("notes", weights.notes());
        context.progress(type(), "Weights for your request: " + weights.describe()
                + (weights.notes().isEmpty() ? "" : " (" + String.join("; ", weights.notes()) + ")"), weightData);

        List<PlaceCandidate> candidates = context.candidates();
        LocalDate today = LocalDate.now(clock);
        Map<String, ScoreBreakdown> scores = new HashMap<>();
        for (PlaceCandidate p : candidates) {
            scores.put(p.id(), scorer.score(p, context.menuReadings().get(p.id()), context.reviewInsights().get(p.id()),
                    context.query(), weights, today));
        }
        List<PlaceCandidate> sorted = new ArrayList<>(candidates);
        // Best score first; a tie goes to the restaurant with more Google reviews.
        sorted.sort(Comparator.comparingDouble((PlaceCandidate p) -> scores.get(p.id()).total()).reversed()
                .thenComparing(PlaceCandidate::userRatingCount, Comparator.reverseOrder()));

        List<RankedRestaurant> ranking = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            PlaceCandidate p = sorted.get(i);
            ranking.add(new RankedRestaurant(p.id(), p.name(), i + 1, p.matchedMeals(), scores.get(p.id())));
        }
        context.rankings(ranking);

        for (RankedRestaurant r : ranking) {
            context.progress(type(), r.placeId(), "#" + r.rank() + " " + r.name() + ": " + r.score().calculation(),
                    Map.of("rank", r.rank(), "score", r.score()));
        }
        String order = ranking.stream().map(r -> r.rank() + ". " + r.name() + " (" + r.score().total() + ")")
                .collect(Collectors.joining("; "));
        context.progress(type(), "Ranking of " + ranking.size() + " restaurants: " + order, Map.of("ranking", ranking));
    }
}
