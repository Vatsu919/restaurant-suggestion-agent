package org.example.restaurantsuggestionagent.answer;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** The LLM's text about one recommended restaurant, in the user's language. */
public record RestaurantWriteUp(
        @JsonPropertyDescription("The id given in the restaurant's facts, copied exactly.")
        String placeId,

        @JsonPropertyDescription("One short line that sums up the place for this user, e.g. "
                + "\"Fully plant-based tapas with a long vegan menu\".")
        String headline,

        @JsonPropertyDescription("One or two sentences on why it suits this user, based on the score calculation "
                + "and the facts.")
        String whyThisPlace,

        @JsonPropertyDescription("Two to four dishes from the facts to try, with their prices as given. "
                + "Only dishes listed in the facts.")
        String whatToOrder,

        @JsonPropertyDescription("What reviewers say, based only on the review quotes in the facts, including "
                + "reviews about the user's request.")
        String whatReviewersSay,

        @JsonPropertyDescription("Anything to be careful about from the facts (critical reviews, penalties, "
                + "dishes without prices). Empty if there is nothing.")
        String watchOutFor) {
}
