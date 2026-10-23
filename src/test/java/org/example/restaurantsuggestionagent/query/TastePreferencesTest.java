package org.example.restaurantsuggestionagent.query;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * "I prefer very little spicy food" is a taste wish: the parser is told to record it as a
 * taste preference (not as something to avoid), and it counts as something the user likes.
 */
class TastePreferencesTest {

    @Test
    void parserIsToldWhereLessSpicyGoes() {
        String request = new ClaudeQueryParser(null, "claude-haiku-5-5")
                .buildRequest("I prefer very less spicy food", LocalDate.of(2026, 10, 24)).rawParams().toString();

        assertThat(request)
                .contains("tastePreferences: how the user likes their food (\"less spicy\" → \"mild, not spicy\")")
                .contains("a mild wish is a preference, not something to avoid")
                .contains("tastePreferences", "Not taste wishes like \"not too spicy\"");
    }

    @Test
    void likedCombinesCuisinesAndTaste() {
        TripQuery q = new TripQuery("Barcelona", "", List.of(Meal.DINNER), DietPreference.VEGETARIAN,
                DietStrictness.LACTO_OVO, Budget.UNSPECIFIED, List.of("tapas"), List.of(), "", "en", 0, List.of(),
                List.of("mild, not spicy"));

        assertThat(Preferences.liked(q)).containsExactly("tapas", "mild, not spicy");
    }
}
