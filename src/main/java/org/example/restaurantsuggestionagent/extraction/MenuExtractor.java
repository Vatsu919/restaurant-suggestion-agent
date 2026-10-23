package org.example.restaurantsuggestionagent.extraction;

import org.example.restaurantsuggestionagent.query.TripQuery;

/** Reads a menu into dishes, prices, translations and diet tags. An interface so tests need no LLM. */
public interface MenuExtractor {

    MenuExtraction extract(String restaurantName, MenuInput menu, TripQuery query);
}
