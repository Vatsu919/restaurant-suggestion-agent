package org.example.restaurantsuggestionagent.answer;

import java.util.List;

import org.example.restaurantsuggestionagent.agent.SkippedRestaurant;

/**
 * What the user gets at the end of a run.
 *
 * @param intro          the LLM's introduction; empty if the text couldn't be written
 * @param skipped        every restaurant dropped along the way, with its reason and links
 * @param skippedSummary the LLM's short paragraph about the skipped restaurants
 */
public record FinalAnswer(String intro, List<Recommendation> recommendations, String skippedSummary,
                          List<SkippedRestaurant> skipped) {

    public FinalAnswer {
        recommendations = List.copyOf(recommendations);
        skipped = List.copyOf(skipped);
    }
}
