package org.example.restaurantsuggestionagent.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.example.restaurantsuggestionagent.agent.RunContext;
import org.example.restaurantsuggestionagent.events.AgentEvent;
import org.example.restaurantsuggestionagent.events.RunEventPublisher;
import org.example.restaurantsuggestionagent.query.Budget;
import org.example.restaurantsuggestionagent.query.DietPreference;
import org.example.restaurantsuggestionagent.query.DietStrictness;
import org.example.restaurantsuggestionagent.query.Meal;
import org.example.restaurantsuggestionagent.query.TripQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DiscoverStepTest {

    private final RunEventPublisher events = new RunEventPublisher();
    private RunContext context;

    @BeforeEach
    void setUp() {
        UUID runId = UUID.randomUUID();
        events.open(runId);
        context = new RunContext(runId, "veg breakfast and dinner", events);
        context.query(new TripQuery("Barcelona", "Gràcia", List.of(Meal.BREAKFAST, Meal.DINNER),
                DietPreference.VEGETARIAN, DietStrictness.LACTO_OVO, Budget.UNSPECIFIED,
                List.of(), List.of(), "", "en", 0, List.of()));
    }

    @Test
    void searchesOnceForAllMealsAndConsidersEveryResultForEachMeal() {
        List<String> queriesSent = new ArrayList<>();
        PlaceSearch fake = (text, lang, max) -> {
            queriesSent.add(text + " | " + lang + " | " + max);
            return List.of(place("cafe"), place("bistro"));
        };

        new DiscoverStep(fake, 20).execute(context);

        assertThat(queriesSent).containsExactly("vegetarian breakfast and dinner in Gràcia, Barcelona | en | 20");
        assertThat(context.candidates()).extracting(PlaceCandidate::id).containsExactly("cafe", "bistro");
        assertThat(context.candidates()).allSatisfy(p ->
                assertThat(p.matchedMeals()).containsExactly(Meal.BREAKFAST, Meal.DINNER));
        assertThat(events.events(context.runId())).extracting(AgentEvent::message).containsExactly(
                "Searching Google Places: \"vegetarian breakfast and dinner in Gràcia, Barcelona\"",
                "Found 2 restaurants");
    }

    @Test
    void noResultsFailsTheRun() {
        DiscoverStep step = new DiscoverStep((text, lang, max) -> List.of(), 20);

        assertThatThrownBy(() -> step.execute(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no restaurants");
    }

    private static PlaceCandidate place(String id) {
        return new PlaceCandidate(id, "Name " + id, null, "https://" + id + ".example", null, 4.5, 100,
                null, "OPERATIONAL", null, null, null, null, null, null, null, null, null);
    }
}
