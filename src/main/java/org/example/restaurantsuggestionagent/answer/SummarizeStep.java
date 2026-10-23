package org.example.restaurantsuggestionagent.answer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.example.restaurantsuggestionagent.agent.PipelineStep;
import org.example.restaurantsuggestionagent.agent.RunContext;
import org.example.restaurantsuggestionagent.discovery.PlaceCandidate;
import org.example.restaurantsuggestionagent.events.StepType;
import org.example.restaurantsuggestionagent.extraction.MenuItem;
import org.example.restaurantsuggestionagent.extraction.MenuReading;
import org.example.restaurantsuggestionagent.rank.RankedRestaurant;
import org.example.restaurantsuggestionagent.reviews.AnalyzeReviewsStep;
import org.example.restaurantsuggestionagent.reviews.ContextReviewFinder;
import org.example.restaurantsuggestionagent.reviews.ReviewInsight;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Step 8 of the pipeline: the final answer. Code gathers the facts for the top-ranked
 * restaurants (links, dishes and prices, review quotes, how the score was reached) and the
 * skipped list; one LLM call turns them into readable text in the user's language. Links
 * are attached in code, so the LLM can't change or invent them.
 */
@Component
@Order(80)
public class SummarizeStep implements PipelineStep {

    private static final Logger log = LoggerFactory.getLogger(SummarizeStep.class);

    /** Quotes per kind (positive, critical, about the request) given for each restaurant. */
    private static final int QUOTES_PER_KIND = 2;

    private final AnswerWriter writer;
    private final int topRestaurants;
    private final int dishesPerRestaurant;

    public SummarizeStep(AnswerWriter writer, @Value("${answer.top-restaurants:5}") int topRestaurants,
                         @Value("${answer.dishes-per-restaurant:8}") int dishesPerRestaurant) {
        this.writer = writer;
        this.topRestaurants = topRestaurants;
        this.dishesPerRestaurant = dishesPerRestaurant;
    }

    @Override
    public StepType type() {
        return StepType.SUMMARIZE;
    }

    @Override
    public String description() {
        return "Writing your recommendations";
    }

    @Override
    public void execute(RunContext context) {
        Map<String, PlaceCandidate> places = context.candidates().stream()
                .collect(Collectors.toMap(PlaceCandidate::id, Function.identity()));
        List<Recommendation> facts = new ArrayList<>();
        for (RankedRestaurant ranked : context.rankings().subList(0, Math.min(topRestaurants, context.rankings().size()))) {
            PlaceCandidate place = places.get(ranked.placeId());
            facts.add(facts(ranked, place, context.menuReadings().get(place.id()),
                    context.reviewInsights().get(place.id())));
        }
        int checked = context.candidates().size() + context.skippedRestaurants().size();

        AnswerText text = null;
        try {
            text = writer.write(context.query(), checked, facts, context.skippedRestaurants());
        } catch (RuntimeException e) {
            // Everything is already decided; without the text, the facts are still shown.
            log.warn("Writing the final answer failed", e);
            context.progress(type(), "The write-up couldn't be made (" + e.getMessage()
                    + "); showing the recommendations without it", Map.of());
        }

        List<Recommendation> recommendations = new ArrayList<>();
        for (Recommendation r : facts) {
            RestaurantWriteUp writeUp = text == null ? null : text.restaurants().stream()
                    .filter(w -> r.placeId().equals(w.placeId())).findFirst().orElse(null);
            recommendations.add(writeUp == null ? r : r.withWriteUp(writeUp));
        }
        FinalAnswer answer = new FinalAnswer(text == null ? "" : text.intro(), recommendations,
                text == null ? "" : text.skippedSummary(), context.skippedRestaurants());
        context.answer(answer);

        for (Recommendation r : recommendations) {
            context.progress(type(), r.placeId(), render(r), Map.of("recommendation", r));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("answer", answer);
        context.progress(type(), (answer.intro().isEmpty() ? "Your top " + recommendations.size() + " restaurants"
                : answer.intro()) + (answer.skippedSummary().isEmpty() ? "" : "\n\n" + answer.skippedSummary()), data);
    }

    /** The facts for one restaurant, from the earlier steps. */
    Recommendation facts(RankedRestaurant ranked, PlaceCandidate place, MenuReading menu, ReviewInsight reviews) {
        // Meal dishes (starters, mains…) before desserts and drinks, and within those the ones matching
        // what the user asked for (e.g. mild) first. Sorting is stable, so menu order is kept otherwise.
        List<Recommendation.Dish> dishes = menu.matchingItems().stream()
                .filter(d -> place.matchedMeals().stream().anyMatch(d::servedAt))
                .sorted(Comparator.comparing((MenuItem d) -> place.matchedMeals().stream().anyMatch(d::isMealDishFor))
                        .thenComparing(MenuItem::matchesPreferences).reversed())
                .limit(dishesPerRestaurant)
                .map(SummarizeStep::dish)
                .toList();
        return new Recommendation(ranked.rank(), place.id(), place.name(), ranked.score().total(),
                ranked.score().calculation(), ranked.meals(), place.address(), place.websiteUri(),
                place.googleMapsUri(), menu.menuUrl(), menu.menuLanguage(), dishes,
                reviews.positive().stream().limit(QUOTES_PER_KIND).map(AnalyzeReviewsStep::quote).toList(),
                reviews.critical().stream().limit(QUOTES_PER_KIND).map(AnalyzeReviewsStep::quote).toList(),
                reviews.contextReviews().stream().limit(QUOTES_PER_KIND).map(SummarizeStep::contextQuote).toList(),
                null);
    }

    private static Recommendation.Dish dish(MenuItem item) {
        return new Recommendation.Dish(item.translatedName(), item.originalName(), item.priceText(), item.description(),
                item.matchesPreferences(), item.course());
    }

    private static String contextQuote(ContextReviewFinder.Match match) {
        return AnalyzeReviewsStep.quote(match.review()) + " (mentions " + String.join(", ", match.mentions()) + ")";
    }

    /** The recommendation as the user reads it: the LLM's text, then the links from the facts. */
    static String render(Recommendation r) {
        StringBuilder out = new StringBuilder();
        out.append(r.rank()).append(". ").append(r.name()).append(" (score ").append(r.score()).append(", for ")
                .append(r.meals().stream().map(m -> m.name().toLowerCase(Locale.ROOT)).sorted()
                        .collect(Collectors.joining(" and "))).append(")\n");
        RestaurantWriteUp w = r.writeUp();
        if (w != null) {
            out.append(w.headline()).append('\n')
                    .append("Why: ").append(w.whyThisPlace()).append('\n')
                    .append("What to order: ").append(w.whatToOrder()).append('\n')
                    .append("Reviewers: ").append(w.whatReviewersSay()).append('\n');
            if (!w.watchOutFor().isBlank()) {
                out.append("Watch out: ").append(w.watchOutFor()).append('\n');
            }
        } else {
            out.append("Dishes for you: ").append(r.dishes().stream()
                    .map(d -> d.name() + (d.price().isBlank() ? "" : " " + d.price()))
                    .collect(Collectors.joining("; "))).append('\n');
        }
        out.append("Menu: ").append(r.menuUrl()).append('\n')
                .append("Website: ").append(r.websiteUri()).append('\n')
                .append("Google Maps: ").append(r.googleMapsUri());
        return out.toString();
    }
}
