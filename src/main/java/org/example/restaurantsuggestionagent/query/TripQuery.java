package org.example.restaurantsuggestionagent.query;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * The user's request in structured form; produced by the LLM from free text.
 * Fields the user did not mention hold an explicit "not given" value (empty
 * string, empty list, UNSPECIFIED) rather than null, so later steps never guess.
 */
public record TripQuery(
        @JsonPropertyDescription("City the user is visiting, in English, e.g. \"Barcelona\". Empty if not mentioned.")
        String city,

        @JsonPropertyDescription("Neighbourhood or area within the city, e.g. \"Gràcia\". Empty if not mentioned.")
        String neighbourhood,

        @JsonPropertyDescription("Meals the user asked about. Empty if none were mentioned.")
        List<Meal> meals,

        @JsonPropertyDescription("VEGETARIAN if the user wants only vegetarian food, NON_VEGETARIAN if they want "
                + "meat or fish dishes, ANY if they want both or did not say.")
        DietPreference diet,

        @JsonPropertyDescription("For vegetarians: LACTO_OVO (eats dairy and egg), NO_EGG, or VEGAN. "
                + "UNSPECIFIED if not vegetarian or not stated.")
        DietStrictness strictness,

        @JsonPropertyDescription("Budget level. UNSPECIFIED if not mentioned.")
        Budget budget,

        @JsonPropertyDescription("Cuisines or dishes the user would like, e.g. [\"tapas\", \"Italian\"].")
        List<String> cuisinePreferences,

        @JsonPropertyDescription("Foods the user wants to avoid or is allergic to, e.g. [\"nuts\", \"mushrooms\"].")
        List<String> avoid,

        @JsonPropertyDescription("Date of the visit as YYYY-MM-DD, resolved against today's date. Empty if not mentioned.")
        String visitDate,

        @JsonPropertyDescription("Language the user wrote in, as an ISO 639-1 code, e.g. \"en\". "
                + "Menus are translated into this language.")
        String userLanguage,

        @JsonPropertyDescription("The most the user wants to spend per person for one meal, as a number in the "
                + "local currency, e.g. 15 for \"I have only €15 for dinner\". 0 if not mentioned.")
        double maxSpendPerPerson,

        @JsonPropertyDescription("What the user says matters most to them, only when they say so, e.g. "
                + "[DIETARY_MATCH] for \"I mainly care about vegetarian options\", [PRICE_MATCH] for "
                + "\"I have only €15\", [RATING] for \"only the best-rated places\", [FOOD_MATCH] for "
                + "\"I really want good tapas\". Empty if they don't say what matters most.")
        List<RankingFactor> priorities) {
}
