package org.example.restaurantsuggestionagent.extraction;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** The LLM's structured reading of one menu. */
public record MenuExtraction(
        @JsonPropertyDescription("What kind of menu this is. REGULAR is the restaurant's standing food menu "
                + "(a fixed-price lunch menu served every day counts as REGULAR only if it names its dishes).")
        MenuKind menuKind,

        @JsonPropertyDescription("One sentence explaining menuKind, in English, e.g. "
                + "\"A set menu for groups of 10 or more\".")
        String menuKindReason,

        @JsonPropertyDescription("Main language of the menu as an ISO 639-1 code, e.g. \"es\", \"ca\", \"en\".")
        String menuLanguage,

        @JsonPropertyDescription("Currency of the prices as an ISO 4217 code, e.g. \"EUR\". Empty if no prices.")
        String currency,

        @JsonPropertyDescription("The dishes on the menu. Leave out drinks unless they are the only items.")
        List<MenuItem> items,

        @JsonPropertyDescription("True if the menu has more dishes than were listed in items.")
        boolean itemsOmitted) {
}
