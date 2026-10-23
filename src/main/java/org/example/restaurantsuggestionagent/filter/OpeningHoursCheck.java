package org.example.restaurantsuggestionagent.filter;

import org.example.restaurantsuggestionagent.discovery.OpeningHours;

/**
 * Decides whether a restaurant is open at any point during a meal window.
 *
 * <p>Times are converted to minutes since Sunday 00:00 so that periods running past
 * midnight (Fri 20:00 → Sat 01:00) and across the week boundary (Sat → Sun) need no
 * special cases.
 */
final class OpeningHoursCheck {

    enum Result { OPEN, CLOSED, UNKNOWN }

    private static final int DAY = 24 * 60;
    private static final int WEEK = 7 * DAY;

    private OpeningHoursCheck() {
    }

    /**
     * @param googleDay 0 = Sunday … 6 = Saturday; null checks every day of the week
     *                  and answers OPEN if the window overlaps on any of them
     */
    static Result isOpenDuring(OpeningHours hours, TimeWindow window, Integer googleDay) {
        if (hours == null || hours.periods().isEmpty()) {
            return Result.UNKNOWN;
        }
        int windowStart = window.start().getHour() * 60 + window.start().getMinute();
        int windowEnd = window.end().getHour() * 60 + window.end().getMinute();
        if (windowEnd <= windowStart) {
            windowEnd += DAY;
        }

        for (OpeningHours.Period period : hours.periods()) {
            if (period.open() == null) {
                continue;
            }
            if (period.close() == null) {
                return Result.OPEN; // Google's way of saying "open 24 hours"
            }
            int open = minutesOfWeek(period.open());
            int close = minutesOfWeek(period.close());
            if (close <= open) {
                close += WEEK; // e.g. Saturday 22:00 → Sunday 02:00
            }
            for (int day = 0; day < 7; day++) {
                if (googleDay != null && day != googleDay) {
                    continue;
                }
                int start = day * DAY + windowStart;
                int end = day * DAY + windowEnd;
                // Check the period as given and shifted by a week, to catch wrap-around.
                for (int shift : new int[] {0, WEEK, -WEEK}) {
                    if (open + shift < end && close + shift > start) {
                        return Result.OPEN;
                    }
                }
            }
        }
        return Result.CLOSED;
    }

    private static int minutesOfWeek(OpeningHours.Point p) {
        return value(p.day()) * DAY + value(p.hour()) * 60 + value(p.minute());
    }

    private static int value(Integer i) {
        return i == null ? 0 : i;
    }
}
