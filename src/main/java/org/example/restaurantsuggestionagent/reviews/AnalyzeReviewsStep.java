package org.example.restaurantsuggestionagent.reviews;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.example.restaurantsuggestionagent.agent.PipelineStep;
import org.example.restaurantsuggestionagent.agent.RunContext;
import org.example.restaurantsuggestionagent.discovery.PlaceCandidate;
import org.example.restaurantsuggestionagent.discovery.PlaceReview;
import org.example.restaurantsuggestionagent.events.StepType;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Step 6 of the pipeline: sort each restaurant's Google reviews into positive (4–5 stars)
 * and critical (3 stars or less), and find the ones that mention what the user asked for.
 * Done in code without an LLM: the reviews are shown as written. Reviews inform the
 * ranking but never remove a restaurant, so this step skips no one.
 */
@Component
@Order(60)
public class AnalyzeReviewsStep implements PipelineStep {

    /** Review text quoted in a progress message is cut to about this length; full text is in the event data. */
    private static final int SNIPPET_CHARS = 140;

    @Override
    public StepType type() {
        return StepType.ANALYZE_REVIEWS;
    }

    @Override
    public String description() {
        return "Sorting reviews into positive and critical, and finding reviews about what you asked for";
    }

    @Override
    public void execute(RunContext context) {
        List<PlaceCandidate> candidates = context.candidates();
        Map<String, ReviewInsight> insights = new HashMap<>();
        for (PlaceCandidate place : candidates) {
            ReviewInsight insight = insight(place, context);
            insights.put(place.id(), insight);
            report(context, place, insight);
        }
        context.reviewInsights(insights);

        long withContext = insights.values().stream().filter(i -> !i.contextReviews().isEmpty()).count();
        long withCritical = insights.values().stream().filter(i -> !i.critical().isEmpty()).count();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("restaurants", candidates.size());
        data.put("withReviewsAboutRequest", withContext);
        data.put("withCriticalReviews", withCritical);
        context.progress(type(), "Read reviews for " + candidates.size() + " restaurants: " + withContext
                + " have reviews about what you asked for, " + withCritical + " have critical reviews", data);
    }

    private static ReviewInsight insight(PlaceCandidate place, RunContext context) {
        List<PlaceReview> reviews = place.reviews();
        return new ReviewInsight(place.id(), reviews.size(),
                reviews.stream().filter(r -> r.rating() != null && r.rating() >= 4).toList(),
                reviews.stream().filter(r -> r.rating() != null && r.rating() <= 3).toList(),
                ContextReviewFinder.find(reviews, context.query()));
    }

    private void report(RunContext context, PlaceCandidate place, ReviewInsight insight) {
        if (insight.reviewsRead() == 0) {
            context.progress(type(), place.id(), place.name() + ": Google shows no reviews", Map.of());
            return;
        }
        StringBuilder message = new StringBuilder(place.name()).append(": ").append(insight.reviewsRead())
                .append(" Google reviews, ").append(insight.positive().size()).append(" positive, ")
                .append(insight.critical().size()).append(" critical");
        if (!insight.critical().isEmpty()) {
            message.append(". Critical: ").append(quote(insight.critical().get(0)));
        }
        if (!insight.contextReviews().isEmpty()) {
            ContextReviewFinder.Match first = insight.contextReviews().get(0);
            message.append(". About your request (").append(insight.contextReviews().size()).append(" reviews): ")
                    .append(quote(first.review())).append(" (mentions ")
                    .append(String.join(", ", first.mentions())).append(")");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reviewsRead", insight.reviewsRead());
        data.put("positive", insight.positive().stream().map(AnalyzeReviewsStep::view).toList());
        data.put("critical", insight.critical().stream().map(AnalyzeReviewsStep::view).toList());
        data.put("aboutYourRequest", insight.contextReviews().stream().map(m -> {
            Map<String, Object> view = view(m.review());
            view.put("mentions", m.mentions());
            return view;
        }).toList());
        context.progress(type(), place.id(), message.toString(), data);
    }

    /** E.g. ★2 "Very slow service, we waited…" */
    public static String quote(PlaceReview review) {
        String text = text(review).replaceAll("\\s+", " ").strip();
        if (text.length() > SNIPPET_CHARS) {
            int cut = text.lastIndexOf(' ', SNIPPET_CHARS);
            text = text.substring(0, cut > 0 ? cut : SNIPPET_CHARS) + "…";
        }
        return "★" + (review.rating() == null ? "?" : review.rating().intValue()) + " \"" + text + "\"";
    }

    private static Map<String, Object> view(PlaceReview review) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("rating", review.rating());
        view.put("author", review.author());
        view.put("when", review.relativePublishTime());
        view.put("text", text(review));
        view.put("originalLanguage", review.originalLanguage());
        return view;
    }

    /** Google's translation into the user's language, or the original if there is none. */
    private static String text(PlaceReview review) {
        return review.text() == null || review.text().isBlank()
                ? (review.originalText() == null ? "" : review.originalText()) : review.text();
    }
}
