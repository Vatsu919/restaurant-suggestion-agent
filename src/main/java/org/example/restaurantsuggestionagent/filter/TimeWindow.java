package org.example.restaurantsuggestionagent.filter;

import java.time.LocalTime;

/** A daily time range in local time, written in config as "07:00-11:00". */
public record TimeWindow(LocalTime start, LocalTime end) {

    public static TimeWindow parse(String text) {
        String[] parts = text.strip().split("-");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Expected HH:mm-HH:mm, got \"" + text + "\"");
        }
        return new TimeWindow(LocalTime.parse(parts[0].strip()), LocalTime.parse(parts[1].strip()));
    }

    @Override
    public String toString() {
        return start + "–" + end;
    }
}
