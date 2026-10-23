package org.example.restaurantsuggestionagent.discovery;

import java.util.List;

/**
 * Weekly opening hours. Same shape as Google's {@code regularOpeningHours}, so it is
 * read straight from the API response.
 *
 * @param periods             one entry per opening window; a period without {@code close} means open 24 hours
 * @param weekdayDescriptions human-readable lines, e.g. "Monday: 8:00 AM – 1:00 PM"
 */
public record OpeningHours(List<Period> periods, List<String> weekdayDescriptions) {

    public OpeningHours {
        periods = periods == null ? List.of() : List.copyOf(periods);
        weekdayDescriptions = weekdayDescriptions == null ? List.of() : List.copyOf(weekdayDescriptions);
    }

    public record Period(Point open, Point close) {
    }

    /** @param day 0 = Sunday … 6 = Saturday, as Google returns it */
    public record Point(Integer day, Integer hour, Integer minute) {
    }
}
