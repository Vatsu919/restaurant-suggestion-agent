package org.example.restaurantsuggestionagent.filter;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.example.restaurantsuggestionagent.agent.PipelineStep;
import org.example.restaurantsuggestionagent.agent.RunContext;
import org.example.restaurantsuggestionagent.agent.SkipReason;
import org.example.restaurantsuggestionagent.discovery.PlaceCandidate;
import org.example.restaurantsuggestionagent.events.StepType;
import org.example.restaurantsuggestionagent.query.Meal;
import org.example.restaurantsuggestionagent.query.TripQuery;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Step 3 of the pipeline: drop restaurants that can't be recommended. Filters only;
 * order is decided later by the ranking step.
 *
 * <p>A restaurant starts as a candidate for every requested meal (one search covers them
 * all). A meal is dropped when Google says the place doesn't serve it, or when its opening hours have no
 * overlap with that meal's window on the visit day. Unknown flags or missing hours keep
 * the meal. A restaurant with no meals left is skipped.
 */
@Component
@Order(30)
public class FilterStep implements PipelineStep {

    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("EEEE d MMM", Locale.ENGLISH);

    /** Hosts Google sometimes lists as a restaurant's "website"; none of them is one. */
    private static final Map<String, String> SOCIAL_NETWORKS = Map.of(
            "instagram.com", "an Instagram",
            "facebook.com", "a Facebook",
            "tiktok.com", "a TikTok",
            "twitter.com", "an X (Twitter)",
            "x.com", "an X (Twitter)");

    private final MealWindows mealWindows;
    private final double minRating;
    private final int minReviewCount;

    public FilterStep(MealWindows mealWindows,
                      @Value("${filter.min-rating:3.0}") double minRating,
                      @Value("${filter.min-review-count:20}") int minReviewCount) {
        this.mealWindows = mealWindows;
        this.minRating = minRating;
        this.minReviewCount = minReviewCount;
    }

    @Override
    public StepType type() {
        return StepType.FILTER;
    }

    @Override
    public String description() {
        return "Filtering restaurants (open, has a website, rating ≥ " + minRating
                + " from ≥ " + minReviewCount + " reviews, serves and is open for the meal)";
    }

    @Override
    public void execute(RunContext context) {
        TripQuery query = context.query();
        LocalDate visitDate = parseDate(query.visitDate());
        Integer googleDay = visitDate == null ? null : visitDate.getDayOfWeek().getValue() % 7; // Sunday = 0
        String dayLabel = visitDate == null ? "on any day" : "on " + DAY_LABEL.format(visitDate);

        List<PlaceCandidate> kept = new ArrayList<>();
        Map<SkipReason, Integer> skippedByReason = new EnumMap<>(SkipReason.class);

        for (PlaceCandidate place : context.candidates()) {
            Skip skip = basicChecks(place);
            if (skip == null) {
                MealDecision meals = decideMeals(place, query, googleDay, dayLabel);
                if (meals.kept().isEmpty()) {
                    SkipReason reason = meals.dropped().containsValue(Drop.NOT_OPEN)
                            ? SkipReason.NOT_OPEN_FOR_MEAL : SkipReason.NOT_SERVING_MEAL;
                    skip = new Skip(reason, String.join("; ", meals.reasons()));
                } else {
                    PlaceCandidate result = place.withMeals(meals.kept());
                    kept.add(result);
                    reportKept(context, result, meals);
                    continue;
                }
            }
            context.skip(type(), place, skip.reason(), skip.detail());
            skippedByReason.merge(skip.reason(), 1, Integer::sum);
        }

        int total = context.candidates().size();
        if (kept.isEmpty()) {
            throw new IllegalStateException("All " + total + " restaurants were filtered out " + skippedByReason);
        }
        context.candidates(kept);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("kept", kept.size());
        data.put("skipped", total - kept.size());
        data.put("skippedByReason", skippedByReason);
        context.progress(type(), "Kept " + kept.size() + " of " + total + " restaurants"
                + (skippedByReason.isEmpty() ? "" : "; skipped " + (total - kept.size()) + " " + skippedByReason), data);
    }

    private Skip basicChecks(PlaceCandidate place) {
        if ("CLOSED_PERMANENTLY".equals(place.businessStatus())) {
            return new Skip(SkipReason.PERMANENTLY_CLOSED, "Permanently closed");
        }
        if ("CLOSED_TEMPORARILY".equals(place.businessStatus())) {
            return new Skip(SkipReason.TEMPORARILY_CLOSED, "Temporarily closed");
        }
        if (place.websiteUri() == null || place.websiteUri().isBlank()) {
            return new Skip(SkipReason.NO_WEBSITE, "No website listed on Google, so its menu can't be read");
        }
        String social = socialNetworkOf(place.websiteUri());
        if (social != null) {
            return new Skip(SkipReason.NO_WEBSITE,
                    "Google lists " + social + " page instead of a website, so its menu can't be read");
        }
        if (place.rating() == null) {
            return new Skip(SkipReason.LOW_RATING, "No Google rating yet");
        }
        if (place.rating() < minRating) {
            return new Skip(SkipReason.LOW_RATING, "Rated " + place.rating() + ", below the minimum of " + minRating);
        }
        int reviews = place.userRatingCount() == null ? 0 : place.userRatingCount();
        if (reviews < minReviewCount) {
            return new Skip(SkipReason.LOW_RATING,
                    "Only " + reviews + " reviews, below the minimum of " + minReviewCount);
        }
        return null;
    }

    private MealDecision decideMeals(PlaceCandidate place, TripQuery query, Integer googleDay, String dayLabel) {
        // Only meals whose search found the place. Google's flags can remove a meal, never add one.
        Set<Meal> considered = EnumSet.noneOf(Meal.class);
        for (Meal meal : query.meals()) {
            if (place.matchedMeals().contains(meal)) {
                considered.add(meal);
            }
        }

        Set<Meal> kept = EnumSet.noneOf(Meal.class);
        Map<Meal, Drop> dropped = new EnumMap<>(Meal.class);
        List<String> reasons = new ArrayList<>();
        for (Meal meal : considered) {
            String mealName = meal.name().toLowerCase();
            TimeWindow window = mealWindows.of(meal);
            if (Boolean.FALSE.equals(place.serves(meal))) {
                dropped.put(meal, Drop.NOT_SERVED);
                reasons.add("Google lists it as not serving " + mealName);
            } else if (OpeningHoursCheck.isOpenDuring(place.openingHours(), window, googleDay)
                    == OpeningHoursCheck.Result.CLOSED) {
                dropped.put(meal, Drop.NOT_OPEN);
                reasons.add("Not open during " + mealName + " hours (" + window + ") " + dayLabel);
            } else {
                kept.add(meal);
            }
        }
        return new MealDecision(kept, dropped, reasons);
    }

    private void reportKept(RunContext context, PlaceCandidate place, MealDecision meals) {
        String message = place.name() + ": kept for " + names(meals.kept())
                + (meals.dropped().isEmpty() ? ""
                : " (dropped " + names(meals.dropped().keySet()) + ": " + String.join("; ", meals.reasons()) + ")");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("meals", meals.kept());
        data.put("droppedMeals", meals.dropped());
        data.put("rating", place.rating());
        data.put("reviews", place.userRatingCount());
        data.put("websiteUri", place.websiteUri());
        context.progress(type(), place.id(), message, data);
    }

    private static String names(Set<Meal> meals) {
        return String.join(", ", meals.stream().map(m -> m.name().toLowerCase()).toList());
    }

    /** "an Instagram" for an Instagram profile URL, etc.; null for a normal website. */
    private static String socialNetworkOf(String url) {
        String host;
        try {
            host = URI.create(url.strip()).getHost();
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (host == null) {
            return null;
        }
        host = host.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> social : SOCIAL_NETWORKS.entrySet()) {
            if (host.equals(social.getKey()) || host.endsWith("." + social.getKey())) {
                return social.getValue();
            }
        }
        return null;
    }

    private static LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private record Skip(SkipReason reason, String detail) {
    }

    private enum Drop { NOT_SERVED, NOT_OPEN }

    /** @param reasons why each dropped meal was dropped, in words, in meal order */
    private record MealDecision(Set<Meal> kept, Map<Meal, Drop> dropped, List<String> reasons) {
    }
}
