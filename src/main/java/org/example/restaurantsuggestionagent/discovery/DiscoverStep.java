package org.example.restaurantsuggestionagent.discovery;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.example.restaurantsuggestionagent.agent.PipelineStep;
import org.example.restaurantsuggestionagent.agent.RunContext;
import org.example.restaurantsuggestionagent.events.StepType;
import org.example.restaurantsuggestionagent.query.Meal;
import org.example.restaurantsuggestionagent.query.TripQuery;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Step 2 of the pipeline: find candidate restaurants with one Google Places search that
 * covers all requested meals. Every result starts as a candidate for all of them; the
 * filter step then removes the meals a restaurant doesn't serve or isn't open for.
 */
@Component
@Order(20)
public class DiscoverStep implements PipelineStep {

    private final PlaceSearch places;
    private final int maxResults;

    public DiscoverStep(PlaceSearch places, @Value("${places.max-results:20}") int maxResults) {
        this.places = places;
        this.maxResults = maxResults;
    }

    @Override
    public StepType type() {
        return StepType.DISCOVER;
    }

    @Override
    public String description() {
        return "Searching Google Places for restaurants";
    }

    @Override
    public void execute(RunContext context) {
        TripQuery query = context.query();
        String text = SearchQueryBuilder.build(query);
        context.progress(type(), "Searching Google Places: \"" + text + "\"",
                Map.of("query", text, "maxResults", maxResults));

        List<PlaceCandidate> found = places.search(text, query.userLanguage(), maxResults);
        if (found.isEmpty()) {
            throw new IllegalStateException("Google Places found no restaurants for this request");
        }

        Set<Meal> meals = EnumSet.copyOf(query.meals());
        List<PlaceCandidate> candidates = found.stream().map(p -> p.withMeals(meals)).toList();
        context.candidates(candidates);
        context.progress(type(), "Found " + candidates.size() + " restaurants",
                Map.of("restaurants", candidates.stream().map(PlaceCandidate::name).toList()));
    }
}
