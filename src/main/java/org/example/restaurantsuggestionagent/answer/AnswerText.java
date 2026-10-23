package org.example.restaurantsuggestionagent.answer;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** The LLM's structured answer: an introduction, one write-up per restaurant, and the skipped ones in brief. */
public record AnswerText(
        @JsonPropertyDescription("Two or three sentences in the user's language: what was searched, how many "
                + "restaurants were checked, and how the top ones were chosen.")
        String intro,

        @JsonPropertyDescription("One write-up per restaurant in the facts, in the same order.")
        List<RestaurantWriteUp> restaurants,

        @JsonPropertyDescription("A short paragraph in the user's language on why the other restaurants were "
                + "skipped, grouped by reason, naming a few examples. Empty if none were skipped.")
        String skippedSummary) {
}
