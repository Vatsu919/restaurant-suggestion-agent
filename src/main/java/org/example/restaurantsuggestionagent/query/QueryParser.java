package org.example.restaurantsuggestionagent.query;

import java.time.LocalDate;

/** Turns the user's free-text request into a {@link TripQuery}. */
public interface QueryParser {

    TripQuery parse(String userMessage, LocalDate today);
}
