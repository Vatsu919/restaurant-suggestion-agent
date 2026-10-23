package org.example.restaurantsuggestionagent.query;

/**
 * The dimensions a restaurant is scored on. The user's request can name some as what
 * matters most to them; those get more weight in the ranking.
 */
public enum RankingFactor {
    /** How many dishes the user can eat, counting the kinds they asked for double. */
    FOOD_MATCH,
    /** Share of the menu the user can eat. */
    DIETARY_MATCH,
    /** Share of the user's dishes within their budget. */
    PRICE_MATCH,
    /** The Google rating. */
    RATING,
    /** Share of the reviews Google shows that are positive (4–5 stars). */
    POSITIVE_REVIEWS,
    /** Reviews that mention what the user asked for (their diet, meals, cuisines). */
    CONTEXT_REVIEWS;

    /** How the factor is named in messages: FOOD_MATCH → "food match". */
    public String label() {
        return name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }
}
