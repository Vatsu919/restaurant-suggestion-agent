package org.example.restaurantsuggestionagent.query;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.example.restaurantsuggestionagent.agent.PipelineStep;
import org.example.restaurantsuggestionagent.agent.RunContext;
import org.example.restaurantsuggestionagent.events.StepType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Step 1 of the pipeline: understand the request. The LLM extracts the fields;
 * this class applies the defaults and checks that a run is possible.
 */
@Component
@Order(10)
public class ParseQueryStep implements PipelineStep {

    private final QueryParser parser;
    private final Clock clock;

    @Autowired
    public ParseQueryStep(QueryParser parser) {
        this(parser, Clock.systemDefaultZone());
    }

    ParseQueryStep(QueryParser parser, Clock clock) {
        this.parser = parser;
        this.clock = clock;
    }

    @Override
    public StepType type() {
        return StepType.PARSE_QUERY;
    }

    @Override
    public String description() {
        return "Understanding your request";
    }

    @Override
    public void execute(RunContext context) {
        TripQuery raw = parser.parse(context.userMessage(), LocalDate.now(clock));

        if (raw.city() == null || raw.city().isBlank()) {
            throw new IllegalArgumentException(
                    "Couldn't find a city in your request. Please say which city you are visiting.");
        }

        TripQuery query = withDefaults(raw);
        context.query(query);

        if (raw.meals() == null || raw.meals().isEmpty()) {
            context.progress(type(), "No meal mentioned, so searching breakfast, lunch and dinner", Map.of());
        }
        context.progress(type(), summary(query), asData(query));
    }

    /** Fills anything the user left open with the documented default. */
    static TripQuery withDefaults(TripQuery q) {
        return new TripQuery(
                q.city().strip(),
                nonNull(q.neighbourhood()),
                q.meals() == null || q.meals().isEmpty() ? List.of(Meal.values()) : List.copyOf(q.meals()),
                q.diet() == null ? DietPreference.ANY : q.diet(),
                q.diet() != DietPreference.VEGETARIAN ? DietStrictness.UNSPECIFIED
                        : q.strictness() == null || q.strictness() == DietStrictness.UNSPECIFIED
                        ? DietStrictness.LACTO_OVO : q.strictness(),
                q.budget() == null ? Budget.UNSPECIFIED : q.budget(),
                q.cuisinePreferences() == null ? List.of() : List.copyOf(q.cuisinePreferences()),
                q.avoid() == null ? List.of() : List.copyOf(q.avoid()),
                nonNull(q.visitDate()),
                q.userLanguage() == null || q.userLanguage().isBlank() ? "en" : q.userLanguage(),
                Math.max(0, q.maxSpendPerPerson()),
                q.priorities() == null ? List.of() : List.copyOf(q.priorities()));
    }

    private static String summary(TripQuery q) {
        String where = q.neighbourhood().isEmpty() ? q.city() : q.neighbourhood() + ", " + q.city();
        String diet = switch (q.diet()) {
            case VEGETARIAN -> "vegetarian (" + q.strictness().name().toLowerCase().replace('_', '-') + ")";
            case NON_VEGETARIAN -> "non-vegetarian";
            case ANY -> "vegetarian and non-vegetarian";
        };
        return "Looking for " + diet + " " + q.meals().stream().map(m -> m.name().toLowerCase()).toList()
                + " in " + where
                + (q.maxSpendPerPerson() > 0 ? ", up to " + q.maxSpendPerPerson() + " per person" : "")
                + (q.priorities().isEmpty() ? "" : ", mainly caring about " + q.priorities());
    }

    private static Map<String, Object> asData(TripQuery q) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("city", q.city());
        data.put("neighbourhood", q.neighbourhood());
        data.put("meals", q.meals());
        data.put("diet", q.diet());
        data.put("strictness", q.strictness());
        data.put("budget", q.budget());
        data.put("cuisinePreferences", q.cuisinePreferences());
        data.put("avoid", q.avoid());
        data.put("visitDate", q.visitDate());
        data.put("userLanguage", q.userLanguage());
        data.put("maxSpendPerPerson", q.maxSpendPerPerson());
        data.put("priorities", q.priorities());
        return data;
    }

    private static String nonNull(String s) {
        return s == null ? "" : s.strip();
    }
}
