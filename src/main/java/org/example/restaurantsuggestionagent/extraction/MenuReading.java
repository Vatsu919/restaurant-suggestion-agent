package org.example.restaurantsuggestionagent.extraction;

import java.util.List;

import org.example.restaurantsuggestionagent.query.Meal;

/**
 * One restaurant's menu after reading: every dish, and the ones that suit the user.
 *
 * @param menuUrl       the page or PDF the dishes come from, shown to the user
 * @param textTruncated true if the menu text was longer than the limit and only its start was read
 */
public record MenuReading(String placeId, String menuUrl, MenuKind kind, String menuLanguage, String currency,
                          List<MenuItem> items, List<MenuItem> matchingItems, boolean itemsOmitted,
                          boolean textTruncated) {

    public MenuReading {
        items = List.copyOf(items);
        matchingItems = List.copyOf(matchingItems);
    }

    /** Dishes served at {@code meal}. */
    public List<MenuItem> itemsFor(Meal meal) {
        return items.stream().filter(i -> i.servedAt(meal)).toList();
    }

    /** Dishes served at {@code meal} that suit the user. */
    public List<MenuItem> matchingFor(Meal meal) {
        return matchingItems.stream().filter(i -> i.servedAt(meal)).toList();
    }
}
