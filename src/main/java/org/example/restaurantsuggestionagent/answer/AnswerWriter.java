package org.example.restaurantsuggestionagent.answer;

import java.util.List;

import org.example.restaurantsuggestionagent.agent.SkippedRestaurant;
import org.example.restaurantsuggestionagent.query.TripQuery;

/** Writes the final answer from the facts. An interface so tests need no LLM. */
public interface AnswerWriter {

    AnswerText write(TripQuery query, int restaurantsChecked, List<Recommendation> recommendations,
                     List<SkippedRestaurant> skipped);
}
