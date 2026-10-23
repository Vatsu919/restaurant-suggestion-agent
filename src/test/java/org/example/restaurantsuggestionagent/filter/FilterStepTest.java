package org.example.restaurantsuggestionagent.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.example.restaurantsuggestionagent.agent.RunContext;
import org.example.restaurantsuggestionagent.agent.SkipReason;
import org.example.restaurantsuggestionagent.agent.SkippedRestaurant;
import org.example.restaurantsuggestionagent.discovery.OpeningHours;
import org.example.restaurantsuggestionagent.discovery.OpeningHours.Period;
import org.example.restaurantsuggestionagent.discovery.OpeningHours.Point;
import org.example.restaurantsuggestionagent.discovery.PlaceCandidate;
import org.example.restaurantsuggestionagent.events.AgentEvent;
import org.example.restaurantsuggestionagent.events.EventStatus;
import org.example.restaurantsuggestionagent.events.RunEventPublisher;
import org.example.restaurantsuggestionagent.query.Budget;
import org.example.restaurantsuggestionagent.query.DietPreference;
import org.example.restaurantsuggestionagent.query.DietStrictness;
import org.example.restaurantsuggestionagent.query.Meal;
import org.example.restaurantsuggestionagent.query.TripQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FilterStepTest {

    /** 2026-10-30 is a Friday (Google day 5). */
    private static final int FRIDAY = 5;

    private final RunEventPublisher events = new RunEventPublisher();
    private final FilterStep step = new FilterStep(new MealWindows("07:00-11:00", "12:00-15:00", "18:00-22:00"), 4.0, 20);
    private RunContext context;

    @BeforeEach
    void setUp() {
        UUID runId = UUID.randomUUID();
        events.open(runId);
        context = new RunContext(runId, "veg breakfast and dinner", events);
        context.query(query(List.of(Meal.BREAKFAST, Meal.DINNER), "2026-10-30"));
    }

    @Test
    void skipsClosedNoWebsiteAndLowRatingWithReasons() {
        context.candidates(List.of(
                place("closed").status("CLOSED_PERMANENTLY").build(),
                place("temp").status("CLOSED_TEMPORARILY").build(),
                place("no-web").website(null).build(),
                place("low").rating(3.8, 500).build(),
                place("few").rating(4.8, 5).build(),
                place("unrated").rating(null, null).build(),
                place("good").build()));

        step.execute(context);

        assertThat(context.candidates()).extracting(PlaceCandidate::id).containsExactly("good");
        assertThat(context.skippedRestaurants()).extracting(SkippedRestaurant::id, SkippedRestaurant::reason)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("closed", SkipReason.PERMANENTLY_CLOSED),
                        org.assertj.core.groups.Tuple.tuple("temp", SkipReason.TEMPORARILY_CLOSED),
                        org.assertj.core.groups.Tuple.tuple("no-web", SkipReason.NO_WEBSITE),
                        org.assertj.core.groups.Tuple.tuple("low", SkipReason.LOW_RATING),
                        org.assertj.core.groups.Tuple.tuple("few", SkipReason.LOW_RATING),
                        org.assertj.core.groups.Tuple.tuple("unrated", SkipReason.LOW_RATING));
        assertThat(skipMessages()).contains(
                "Name no-web: No website listed on Google, so its menu can't be read",
                "Name low: Rated 3.8, below the minimum of 4.0",
                "Name few: Only 5 reviews, below the minimum of 20");
    }

    @Test
    void socialMediaProfileIsNotAWebsite() {
        // The La PerraVerde / La Ikas case.
        context.candidates(List.of(
                place("ig").website("https://instagram.com/laperraverdebarcelona").build(),
                place("fb").website("https://www.facebook.com/someone/").build(),
                place("good").build()));

        step.execute(context);

        assertThat(context.candidates()).extracting(PlaceCandidate::id).containsExactly("good");
        assertThat(context.skippedRestaurants()).extracting(SkippedRestaurant::reason)
                .containsOnly(SkipReason.NO_WEBSITE);
        assertThat(skipMessages()).containsExactly(
                "Name ig: Google lists an Instagram page instead of a website, so its menu can't be read",
                "Name fb: Google lists a Facebook page instead of a website, so its menu can't be read");
    }

    @Test
    void servesFalseDropsThatMealOnly() {
        context.candidates(List.of(place("p").meals(Meal.BREAKFAST, Meal.DINNER).serves(false, null, true).build()));

        step.execute(context);

        PlaceCandidate kept = context.candidates().get(0);
        assertThat(kept.matchedMeals()).containsExactly(Meal.DINNER);
        assertThat(progressMessages())
                .contains("Name p: kept for dinner (dropped breakfast: Google lists it as not serving breakfast)");
    }

    @Test
    void servesFalseForEveryMealSkipsTheRestaurant() {
        context.candidates(List.of(place("p").meals(Meal.BREAKFAST).serves(false, null, null).build(),
                place("other").build()));

        step.execute(context);

        assertThat(context.skippedRestaurants()).singleElement().satisfies(s -> {
            assertThat(s.reason()).isEqualTo(SkipReason.NOT_SERVING_MEAL);
            assertThat(s.detail()).isEqualTo("Google lists it as not serving breakfast");
        });
    }

    @Test
    void unknownServesFlagKeepsTheMeal() {
        context.candidates(List.of(place("p").meals(Meal.BREAKFAST).serves(null, null, null).build()));

        step.execute(context);

        assertThat(context.candidates().get(0).matchedMeals()).containsExactly(Meal.BREAKFAST);
    }

    @Test
    void servesTrueDoesNotAddAMealTheSearchDidNotFind() {
        // The Swagatam case: found only by the dinner search; Google says it serves breakfast too.
        context.candidates(List.of(place("p").meals(Meal.DINNER).serves(true, null, true).build()));

        step.execute(context);

        assertThat(context.candidates().get(0).matchedMeals()).containsExactly(Meal.DINNER);
    }

    @Test
    void servesTrueForAMealTheUserDidNotAskForIsIgnored() {
        context.candidates(List.of(place("p").meals(Meal.BREAKFAST).serves(true, true, null).build()));

        step.execute(context);

        assertThat(context.candidates().get(0).matchedMeals()).containsExactly(Meal.BREAKFAST);
    }

    @Test
    void closedDuringAMealDropsThatMealEvenIfGoogleSaysItServesIt() {
        // The Coco Social House case: found by both searches, Friday hours 08:00–17:00.
        OpeningHours daytime = new OpeningHours(
                List.of(new Period(new Point(FRIDAY, 8, 0), new Point(FRIDAY, 17, 0))), List.of());
        context.candidates(List.of(place("p").meals(Meal.BREAKFAST, Meal.DINNER)
                .serves(true, null, true).hours(daytime).build()));

        step.execute(context);

        assertThat(context.candidates().get(0).matchedMeals()).containsExactly(Meal.BREAKFAST);
        assertThat(progressMessages()).contains("Name p: kept for breakfast "
                + "(dropped dinner: Not open during dinner hours (18:00–22:00) on Friday 30 Oct)");
    }

    @Test
    void closedDuringItsOnlyMealSkipsTheRestaurant() {
        OpeningHours lunchOnly = new OpeningHours(
                List.of(new Period(new Point(FRIDAY, 12, 0), new Point(FRIDAY, 16, 0))), List.of());
        context.candidates(List.of(place("p").meals(Meal.BREAKFAST).serves(true, null, null).hours(lunchOnly).build(),
                place("other").build()));

        step.execute(context);

        assertThat(context.candidates()).extracting(PlaceCandidate::id).containsExactly("other");
        assertThat(context.skippedRestaurants()).singleElement().satisfies(s -> {
            assertThat(s.reason()).isEqualTo(SkipReason.NOT_OPEN_FOR_MEAL);
            assertThat(s.detail()).isEqualTo("Not open during breakfast hours (07:00–11:00) on Friday 30 Oct");
        });
    }

    @Test
    void withoutVisitDateOnlyClosedEveryDayCounts() {
        context.query(query(List.of(Meal.DINNER), ""));
        OpeningHours lunchOnly = new OpeningHours(
                List.of(new Period(new Point(FRIDAY, 12, 0), new Point(FRIDAY, 16, 0))), List.of());
        context.candidates(List.of(place("p").meals(Meal.DINNER).hours(lunchOnly).build(), place("other").build()));

        step.execute(context);

        assertThat(context.skippedRestaurants()).singleElement().extracting(SkippedRestaurant::detail)
                .isEqualTo("Not open during dinner hours (18:00–22:00) on any day");
    }

    @Test
    void missingOpeningHoursKeepTheMeal() {
        context.candidates(List.of(place("p").meals(Meal.DINNER).serves(null, null, true).hours(null).build()));

        step.execute(context);

        assertThat(context.candidates().get(0).matchedMeals()).containsExactly(Meal.DINNER);
    }

    @Test
    void reportsSummary() {
        context.candidates(List.of(place("a").build(), place("b").website(null).build()));

        step.execute(context);

        assertThat(progressMessages()).last().isEqualTo("Kept 1 of 2 restaurants; skipped 1 {NO_WEBSITE=1}");
    }

    @Test
    void everythingFilteredOutFailsTheRun() {
        context.candidates(List.of(place("a").website(null).build(), place("b").rating(2.0, 100).build()));

        assertThatThrownBy(() -> step.execute(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("All 2 restaurants were filtered out");
    }

    private List<String> skipMessages() {
        return events.events(context.runId()).stream()
                .filter(e -> e.status() == EventStatus.SKIPPED).map(AgentEvent::message).toList();
    }

    private List<String> progressMessages() {
        return events.events(context.runId()).stream()
                .filter(e -> e.status() == EventStatus.PROGRESS).map(AgentEvent::message).toList();
    }

    private static TripQuery query(List<Meal> meals, String visitDate) {
        return new TripQuery("Barcelona", "", meals, DietPreference.VEGETARIAN, DietStrictness.LACTO_OVO,
                Budget.UNSPECIFIED, List.of(), List.of(), visitDate, "en", 0, List.of(), List.of());
    }

    private static Builder place(String id) {
        return new Builder(id);
    }

    /** Defaults describe a restaurant that passes every filter for breakfast and dinner. */
    private static final class Builder {
        private final String id;
        private String status = "OPERATIONAL";
        private String website;
        private Double rating = 4.5;
        private Integer reviews = 200;
        private Boolean breakfast, lunch, dinner;
        private OpeningHours hours;
        private Set<Meal> meals = Set.of(Meal.BREAKFAST, Meal.DINNER);

        Builder(String id) {
            this.id = id;
            this.website = "https://" + id + ".example";
        }

        Builder status(String s) { status = s; return this; }
        Builder website(String w) { website = w; return this; }
        Builder rating(Double r, Integer n) { rating = r; reviews = n; return this; }
        Builder serves(Boolean b, Boolean l, Boolean d) { breakfast = b; lunch = l; dinner = d; return this; }
        Builder hours(OpeningHours h) { hours = h; return this; }
        Builder meals(Meal... m) { meals = Set.of(m); return this; }

        PlaceCandidate build() {
            return new PlaceCandidate(id, "Name " + id, null, website, "https://maps.example/" + id, rating, reviews,
                    null, status, null, breakfast, lunch, dinner, null, hours, List.of(), meals, List.of());
        }
    }
}
