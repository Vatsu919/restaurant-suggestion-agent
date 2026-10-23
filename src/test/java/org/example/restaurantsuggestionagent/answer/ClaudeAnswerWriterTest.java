package org.example.restaurantsuggestionagent.answer;

import org.example.restaurantsuggestionagent.extraction.Course;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.example.restaurantsuggestionagent.agent.SkipReason;
import org.example.restaurantsuggestionagent.agent.SkippedRestaurant;
import org.example.restaurantsuggestionagent.events.StepType;
import org.example.restaurantsuggestionagent.query.Budget;
import org.example.restaurantsuggestionagent.query.DietPreference;
import org.example.restaurantsuggestionagent.query.DietStrictness;
import org.example.restaurantsuggestionagent.query.Meal;
import org.example.restaurantsuggestionagent.query.TripQuery;
import org.junit.jupiter.api.Test;

/** Runs offline: checks the request we would send, without calling the API. */
class ClaudeAnswerWriterTest {

    @Test
    void requestCarriesTheFactsButNoLinks() {
        TripQuery query = new TripQuery("Barcelona", "Gràcia", List.of(Meal.DINNER), DietPreference.VEGETARIAN,
                DietStrictness.LACTO_OVO, Budget.UNSPECIFIED, List.of("spicy"), List.of(), "", "es", 0, List.of(), List.of());
        Recommendation velada = new Recommendation(1, "p1", "Velada", 94.6, "food match 100 × 29% = 28.6 (…) → 94.6",
                Set.of(Meal.DINNER), "Carrer X", "https://velada.example/", "https://maps.example/velada",
                "https://velada.example/menu.pdf", "es",
                List.of(new Recommendation.Dish("Patatas bravas", "Bravas Velada", "6 €", "Spicy potatoes", true, Course.MAIN),
                        new Recommendation.Dish("Hummus", "Hummus", "8 €", "Chickpeas", false, Course.MAIN)),
                List.of("★5 \"Amazing\""), List.of(), List.of("★5 \"Best vegan tapas\" (mentions vegan)"), null);
        SkippedRestaurant skipped = new SkippedRestaurant("x", "Fat Veggies", "https://fat.example/", "https://maps/x",
                StepType.RESOLVE_MENU, SkipReason.MENU_IS_IMAGE,
                "The menu is only published as images (on /menu) (checked: https://fat.example/ → homepage)");

        String request = new ClaudeAnswerWriter(null, "claude-haiku-5-5")
                .buildRequest(query, 14, List.of(velada), List.of(skipped)).rawParams().toString();

        assertThat(request)
                .contains("claude-haiku-5-5", "User's language: es", "City: Barcelona (Gràcia)")
                .contains("<restaurant id=\"p1\" rank=\"1\">", "Dishes or cuisines the user likes: spicy")
                .contains("- Patatas bravas | Bravas Velada | MAIN | 6 € | Spicy potatoes [matches what the user likes]",
                        "- Hummus | Hummus | MAIN | 8 € | Chickpeas\n",
                        "not only desserts or drinks; mention a dessert only as an extra",
                        "prefer dishes marked [matches what the user likes]")
                .contains("Score calculation: food match 100 × 29% = 28.6", "★5 \"Best vegan tapas\" (mentions vegan)")
                .contains("Fat Veggies: MENU_IS_IMAGE — The menu is only published as images (on /menu)")
                .contains("whatToOrder", "skippedSummary")
                .doesNotContain("velada.example", "maps.example", "(checked:");
    }
}
