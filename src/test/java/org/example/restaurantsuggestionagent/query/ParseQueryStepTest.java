package org.example.restaurantsuggestionagent.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.example.restaurantsuggestionagent.agent.RunContext;
import org.example.restaurantsuggestionagent.events.AgentEvent;
import org.example.restaurantsuggestionagent.events.RunEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ParseQueryStepTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-10-23T10:00:00Z"), ZoneOffset.UTC);

    private final RunEventPublisher events = new RunEventPublisher();
    private RunContext context;

    @BeforeEach
    void setUp() {
        UUID runId = UUID.randomUUID();
        events.open(runId);
        context = new RunContext(runId, "veg breakfast in Barcelona", events);
    }

    @Test
    void storesParsedQueryAndReportsSummary() {
        TripQuery parsed = new TripQuery("Barcelona", "Gràcia", List.of(Meal.BREAKFAST, Meal.DINNER),
                DietPreference.VEGETARIAN, DietStrictness.NO_EGG, Budget.MODERATE,
                List.of("tapas"), List.of("nuts"), "2026-10-24", "en", 0, List.of(), List.of());

        new ParseQueryStep((msg, today) -> parsed, FIXED).execute(context);

        assertThat(context.query()).isEqualTo(parsed);
        assertThat(events.events(context.runId())).extracting(AgentEvent::message)
                .containsExactly("Looking for vegetarian (no-egg) [breakfast, dinner] in Gràcia, Barcelona");
    }

    @Test
    void passesTodaysDateSoRelativeDatesCanBeResolved() {
        AtomicReference<LocalDate> seen = new AtomicReference<>();
        QueryParser parser = (msg, today) -> {
            seen.set(today);
            return minimal("Barcelona");
        };

        new ParseQueryStep(parser, FIXED).execute(context);

        assertThat(seen.get()).isEqualTo(LocalDate.of(2026, 10, 23));
    }

    @Test
    void appliesDefaultsWhenUserLeavesThingsOpen() {
        new ParseQueryStep((msg, today) -> minimal("Barcelona"), FIXED).execute(context);

        TripQuery q = context.query();
        assertThat(q.meals()).containsExactly(Meal.BREAKFAST, Meal.LUNCH, Meal.DINNER);
        assertThat(q.diet()).isEqualTo(DietPreference.ANY);
        assertThat(q.strictness()).isEqualTo(DietStrictness.UNSPECIFIED);
        assertThat(q.budget()).isEqualTo(Budget.UNSPECIFIED);
        assertThat(q.userLanguage()).isEqualTo("en", 0, List.of());
        assertThat(events.events(context.runId())).extracting(AgentEvent::message)
                .contains("No meal mentioned, so searching breakfast, lunch and dinner");
    }

    @Test
    void vegetarianWithoutStrictnessMeansLactoOvo() {
        TripQuery parsed = new TripQuery("Barcelona", "", List.of(Meal.LUNCH), DietPreference.VEGETARIAN,
                DietStrictness.UNSPECIFIED, Budget.UNSPECIFIED, List.of(), List.of(), "", "en", 0, List.of(), List.of());

        new ParseQueryStep((msg, today) -> parsed, FIXED).execute(context);

        assertThat(context.query().strictness()).isEqualTo(DietStrictness.LACTO_OVO);
    }

    @Test
    void missingCityStopsTheRunWithAClearMessage() {
        ParseQueryStep step = new ParseQueryStep((msg, today) -> minimal(""), FIXED);

        assertThatThrownBy(() -> step.execute(context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("which city");
    }

    private static TripQuery minimal(String city) {
        return new TripQuery(city, null, List.of(), DietPreference.ANY, null, null, null, null, null, null, 0, null, List.of());
    }
}
