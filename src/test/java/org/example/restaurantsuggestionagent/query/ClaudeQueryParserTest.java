package org.example.restaurantsuggestionagent.query;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/** Runs offline: checks the request we would send, without calling the API. */
class ClaudeQueryParserTest {

    @Test
    void requestCarriesSchemaDerivedFromTripQueryAndTodaysDate() {
        ClaudeQueryParser parser = new ClaudeQueryParser(null, "claude-opus-5-5");

        String request = parser.buildRequest("veg dinner in Barcelona", LocalDate.of(2026, 10, 23))
                .rawParams().toString();

        assertThat(request)
                .contains("claude-opus-5-5")
                .contains("Today's date: 2026-10-23")
                .contains("visitDate", "strictness", "NON_VEGETARIAN", "LACTO_OVO");
    }
}
