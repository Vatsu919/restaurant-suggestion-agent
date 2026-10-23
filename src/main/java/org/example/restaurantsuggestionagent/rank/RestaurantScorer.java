package org.example.restaurantsuggestionagent.rank;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.example.restaurantsuggestionagent.discovery.PlaceCandidate;
import org.example.restaurantsuggestionagent.discovery.PlaceReview;
import org.example.restaurantsuggestionagent.extraction.DietMatcher;
import org.example.restaurantsuggestionagent.extraction.MenuItem;
import org.example.restaurantsuggestionagent.extraction.MenuReading;
import org.example.restaurantsuggestionagent.query.Budget;
import org.example.restaurantsuggestionagent.query.DietPreference;
import org.example.restaurantsuggestionagent.query.Preferences;
import org.example.restaurantsuggestionagent.query.RankingFactor;
import org.example.restaurantsuggestionagent.query.TripQuery;
import org.example.restaurantsuggestionagent.reviews.ReviewInsight;

/**
 * Scores one restaurant. Each dimension is a simple rule giving 0 to 100; the dimensions
 * are combined with the request's {@link RankingWeights}; then 5 points are taken off for
 * each bad sign. Pure code, no LLM; every point comes with a reason.
 *
 * <p>Only dishes served at the meals the restaurant is considered for count ("relevant dishes").
 */
public final class RestaurantScorer {

    /** Food match: points per dish the user can eat, and per dish of a kind they asked for. Max 100. */
    static final int POINTS_PER_DISH = 5;
    static final int POINTS_PER_LIKED_DISH = 10;

    /** Context reviews: the starting score, and points per good (★4–5) or bad (★1–2) review. */
    static final int CONTEXT_START = 50;
    static final int POINTS_PER_CONTEXT_REVIEW = 10;

    /** Points taken off for each bad sign. */
    static final double PENALTY = 5;

    /** If even the newest review is older than this, the place may have changed. */
    static final int STALE_REVIEW_MONTHS = 12;

    private final double minRating;

    /** @param minRating the lowest rating FILTER lets through ({@code filter.min-rating}); it scores 0 here */
    public RestaurantScorer(double minRating) {
        this.minRating = minRating;
    }

    /** The rule behind each weighted mark, in words, for the run's explanation. */
    public List<String> rules(RankingWeights weights, TripQuery query) {
        List<String> rules = new ArrayList<>();
        if (weights.of(RankingFactor.FOOD_MATCH) > 0) {
            rules.add("food match = " + POINTS_PER_DISH + " points per dish you can eat"
                    + (Preferences.liked(query).isEmpty() ? "" : ", " + POINTS_PER_LIKED_DISH + " per "
                    + String.join("/", Preferences.liked(query)) + " dish") + ", max 100");
        }
        if (weights.of(RankingFactor.DIETARY_MATCH) > 0) {
            rules.add("dietary match = % of the menu you can eat");
        }
        if (weights.of(RankingFactor.PRICE_MATCH) > 0) {
            rules.add(query.maxSpendPerPerson() > 0
                    ? "price match = % of your dishes within " + format(query.maxSpendPerPerson())
                    : "price match = 100 if Google's price level fits your budget, 50 if one level off, else 0");
        }
        if (weights.of(RankingFactor.RATING) > 0) {
            rules.add("rating = ★" + format(minRating) + " → 0, ★" + format((minRating + 5) / 2) + " → 50, ★5 → 100");
        }
        if (weights.of(RankingFactor.POSITIVE_REVIEWS) > 0) {
            rules.add("positive reviews = % of the reviews Google shows that are ★4–5");
        }
        if (weights.of(RankingFactor.CONTEXT_REVIEWS) > 0) {
            rules.add("context reviews = " + CONTEXT_START + ", +" + POINTS_PER_CONTEXT_REVIEW + " per good and −"
                    + POINTS_PER_CONTEXT_REVIEW + " per bad review about your request");
        }
        rules.add("score = each mark × its weight, added up, minus " + format(PENALTY) + " for each bad sign "
                + "(a ★1–2 review, a critical review mentioning hygiene, food safety, a diet mistake, rude staff "
                + "or overcharging, Google saying no vegetarian food, no review in the last year)");
        return rules;
    }

    public ScoreBreakdown score(PlaceCandidate place, MenuReading menu, ReviewInsight reviews, TripQuery query,
                                RankingWeights weights, LocalDate today) {
        List<MenuItem> relevant = relevant(menu.items(), place);
        List<MenuItem> suitable = relevant(menu.matchingItems(), place);

        List<ScorePart> parts = new ArrayList<>();
        add(parts, weights, RankingFactor.FOOD_MATCH, () -> foodMatch(suitable, query));
        add(parts, weights, RankingFactor.DIETARY_MATCH, () -> dietaryMatch(relevant, suitable, query));
        add(parts, weights, RankingFactor.PRICE_MATCH, () -> priceMatch(suitable, place, query));
        add(parts, weights, RankingFactor.RATING, () -> rating(place, minRating));
        add(parts, weights, RankingFactor.POSITIVE_REVIEWS, () -> positiveReviews(reviews));
        add(parts, weights, RankingFactor.CONTEXT_REVIEWS, () -> contextReviews(reviews));

        double positive = parts.stream().mapToDouble(ScorePart::points).sum();
        List<Penalty> penalties = penalties(place, reviews, query, today);
        double penaltyPoints = penalties.stream().mapToDouble(Penalty::points).sum();
        return new ScoreBreakdown(parts, penalties, round1(positive), penaltyPoints,
                round1(Math.max(0, positive - penaltyPoints)));
    }

    /** A dimension's score and its reason, before weighting. */
    record Dimension(double score, String reason) {
    }

    private interface DimensionSource {
        Dimension get();
    }

    /** Dimensions with weight 0 (e.g. price without a budget) are left out entirely. */
    private static void add(List<ScorePart> parts, RankingWeights weights, RankingFactor factor, DimensionSource source) {
        double weight = weights.of(factor);
        if (weight <= 0) {
            return;
        }
        Dimension d = source.get();
        parts.add(new ScorePart(factor, Math.round(d.score()), round2(weight), round1(d.score() * weight), d.reason()));
    }

    /** Dishes served at a meal the restaurant is still considered for. */
    static List<MenuItem> relevant(List<MenuItem> dishes, PlaceCandidate place) {
        return dishes.stream().filter(d -> place.matchedMeals().stream().anyMatch(d::servedAt)).toList();
    }

    /** 5 points per dish the user can eat, 10 if it's a kind they asked for (e.g. tapas); max 100. */
    static Dimension foodMatch(List<MenuItem> suitable, TripQuery query) {
        long liked = suitable.stream().filter(MenuItem::matchesPreferences).count();
        long other = suitable.size() - liked;
        double score = Math.min(100, other * POINTS_PER_DISH + liked * POINTS_PER_LIKED_DISH);
        List<String> likes = Preferences.liked(query);
        String reason = likes.isEmpty() ? suitable.size() + " dishes you can eat"
                : suitable.size() + " dishes you can eat, " + liked + " of them " + String.join("/", likes);
        return new Dimension(score, reason);
    }

    /** Percentage of the menu the user can eat. */
    static Dimension dietaryMatch(List<MenuItem> relevant, List<MenuItem> suitable, TripQuery query) {
        return new Dimension(percent(suitable.size(), relevant.size()),
                suitable.size() + " of " + relevant.size() + " dishes suit " + DietMatcher.describe(query));
    }

    /**
     * With an amount: percentage of the user's priced dishes within it. With only a budget
     * level: Google's price level matches (100), is one level off (50), or further (0).
     */
    static Dimension priceMatch(List<MenuItem> suitable, PlaceCandidate place, TripQuery query) {
        double budget = query.maxSpendPerPerson();
        if (budget > 0) {
            List<Double> prices = suitable.stream().map(MenuItem::price).filter(p -> p > 0).toList();
            if (prices.isEmpty()) {
                return new Dimension(50, "no prices to compare with your budget");
            }
            long within = prices.stream().filter(p -> p <= budget).count();
            return new Dimension(percent(within, prices.size()),
                    within + " of " + prices.size() + " dishes within " + format(budget));
        }
        int level = priceLevel(place.priceLevel());
        if (level < 0) {
            return new Dimension(50, "price level unknown");
        }
        int target = switch (query.budget()) {
            case CHEAP -> 1;
            case MODERATE -> 2;
            case EXPENSIVE -> 3;
            case UNSPECIFIED -> throw new IllegalStateException("PRICE_MATCH has no weight without a budget");
        };
        int distance = query.budget() == Budget.EXPENSIVE && level >= 3 ? 0 : Math.abs(level - target);
        return distance == 0 ? new Dimension(100, "price level fits your budget")
                : distance == 1 ? new Dimension(50, "price level a little off your budget")
                : new Dimension(0, "price level outside your budget");
    }

    /**
     * Where the rating sits between the lowest FILTER lets through and 5 stars, as a percentage.
     * With filter.min-rating=3.0: ★3.0 → 0, ★4.0 → 50, ★5.0 → 100. FILTER also requires
     * filter.min-review-count reviews, so every rating here rests on enough reviews.
     */
    static Dimension rating(PlaceCandidate place, double minRating) {
        return new Dimension(Math.max(0, Math.min(100, 100 * (place.rating() - minRating) / (5 - minRating))),
                "Google ★" + place.rating() + " (" + place.userRatingCount() + " reviews)");
    }

    /** Percentage of the reviews Google shows that are positive (4–5 stars); 50 if it shows none. */
    static Dimension positiveReviews(ReviewInsight reviews) {
        int all = reviews.positive().size() + reviews.critical().size();
        if (all == 0) {
            return new Dimension(50, "Google shows no reviews");
        }
        return new Dimension(percent(reviews.positive().size(), all),
                reviews.positive().size() + " of " + all + " reviews are positive");
    }

    /** Starts at 50; +10 for each ★4–5 review about the user's request, −10 for each ★1–2 one. */
    static Dimension contextReviews(ReviewInsight reviews) {
        List<Double> stars = reviews.contextReviews().stream()
                .map(m -> m.review().rating()).filter(Objects::nonNull).toList();
        long good = stars.stream().filter(s -> s >= 4).count();
        long bad = stars.stream().filter(s -> s <= 2).count();
        double score = Math.max(0, Math.min(100, CONTEXT_START + POINTS_PER_CONTEXT_REVIEW * (good - bad)));
        String reason = stars.isEmpty() ? "no review mentions your request"
                : good + " good and " + bad + " bad reviews about your request";
        return new Dimension(score, reason);
    }

    /** 5 points off for each bad sign. */
    static List<Penalty> penalties(PlaceCandidate place, ReviewInsight reviews, TripQuery query, LocalDate today) {
        List<Penalty> penalties = new ArrayList<>();
        for (PlaceReview review : reviews.critical()) {
            if (review.rating() <= 2) {
                penalties.add(new Penalty(PENALTY, "a ★" + review.rating().intValue() + " review"));
            }
        }
        for (ReviewRedFlags.Found found : ReviewRedFlags.find(reviews.critical())) {
            penalties.add(new Penalty(PENALTY, "a critical review mentions " + found.flag().label));
        }
        if (query.diet() == DietPreference.VEGETARIAN && Boolean.FALSE.equals(place.servesVegetarianFood())) {
            penalties.add(new Penalty(PENALTY, "Google lists it as not serving vegetarian food"));
        }
        newestReview(reviews).filter(newest -> newest.isBefore(today.minusMonths(STALE_REVIEW_MONTHS)))
                .ifPresent(newest -> penalties.add(new Penalty(PENALTY, "no review in the last year (newest "
                        + newest + ")")));
        return penalties;
    }

    /** "PRICE_LEVEL_MODERATE" → 2; -1 if unknown. */
    static int priceLevel(String priceLevel) {
        if (priceLevel == null) {
            return -1;
        }
        return switch (priceLevel.toUpperCase(Locale.ROOT)) {
            case "PRICE_LEVEL_FREE", "PRICE_LEVEL_INEXPENSIVE" -> 1;
            case "PRICE_LEVEL_MODERATE" -> 2;
            case "PRICE_LEVEL_EXPENSIVE" -> 3;
            case "PRICE_LEVEL_VERY_EXPENSIVE" -> 4;
            default -> -1;
        };
    }

    /** Date of the most recent review Google returned; empty if none has a readable date. */
    static Optional<LocalDate> newestReview(ReviewInsight reviews) {
        List<PlaceReview> all = new ArrayList<>(reviews.positive());
        all.addAll(reviews.critical());
        return all.stream().map(PlaceReview::publishTime).filter(Objects::nonNull)
                .map(RestaurantScorer::date).flatMap(Optional::stream).max(LocalDate::compareTo);
    }

    private static Optional<LocalDate> date(String isoTimestamp) {
        try {
            return Optional.of(OffsetDateTime.parse(isoTimestamp).toLocalDate());
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private static double percent(long part, long whole) {
        return 100.0 * part / whole;
    }

    /** 15 → "15", 4.5 → "4.5", 12.75 → "12.75". */
    private static String format(double amount) {
        return java.math.BigDecimal.valueOf(amount).setScale(2, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }

    private static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private static double round2(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
