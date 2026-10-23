package org.example.restaurantsuggestionagent.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.example.restaurantsuggestionagent.filter.OpeningHoursCheck.Result.CLOSED;
import static org.example.restaurantsuggestionagent.filter.OpeningHoursCheck.Result.OPEN;
import static org.example.restaurantsuggestionagent.filter.OpeningHoursCheck.Result.UNKNOWN;

import java.util.List;

import org.example.restaurantsuggestionagent.discovery.OpeningHours;
import org.example.restaurantsuggestionagent.discovery.OpeningHours.Period;
import org.example.restaurantsuggestionagent.discovery.OpeningHours.Point;
import org.junit.jupiter.api.Test;

class OpeningHoursCheckTest {

    private static final int SUNDAY = 0, FRIDAY = 5, SATURDAY = 6;
    private static final TimeWindow BREAKFAST = TimeWindow.parse("07:00-11:00");
    private static final TimeWindow DINNER = TimeWindow.parse("18:00-22:00");

    @Test
    void partialOverlapCountsAsOpen() {
        // Opens at 20:00: overlaps the 18:00–22:00 window, as late-dining cities do.
        OpeningHours hours = hours(period(FRIDAY, 20, 0, FRIDAY, 23, 30));
        assertThat(OpeningHoursCheck.isOpenDuring(hours, DINNER, FRIDAY)).isEqualTo(OPEN);
    }

    @Test
    void noOverlapIsClosed() {
        OpeningHours hours = hours(period(FRIDAY, 12, 0, FRIDAY, 16, 0));
        assertThat(OpeningHoursCheck.isOpenDuring(hours, DINNER, FRIDAY)).isEqualTo(CLOSED);
        assertThat(OpeningHoursCheck.isOpenDuring(hours, BREAKFAST, FRIDAY)).isEqualTo(CLOSED);
    }

    @Test
    void touchingTheWindowEdgeIsNotAnOverlap() {
        OpeningHours hours = hours(period(FRIDAY, 11, 0, FRIDAY, 15, 0));
        assertThat(OpeningHoursCheck.isOpenDuring(hours, BREAKFAST, FRIDAY)).isEqualTo(CLOSED);
    }

    @Test
    void otherDaysDoNotCount() {
        OpeningHours hours = hours(period(SATURDAY, 8, 0, SATURDAY, 12, 0));
        assertThat(OpeningHoursCheck.isOpenDuring(hours, BREAKFAST, FRIDAY)).isEqualTo(CLOSED);
        assertThat(OpeningHoursCheck.isOpenDuring(hours, BREAKFAST, SATURDAY)).isEqualTo(OPEN);
    }

    @Test
    void periodRunningPastMidnight() {
        // Friday 21:00 → Saturday 02:00 covers Friday dinner.
        OpeningHours hours = hours(period(FRIDAY, 21, 0, SATURDAY, 2, 0));
        assertThat(OpeningHoursCheck.isOpenDuring(hours, DINNER, FRIDAY)).isEqualTo(OPEN);
    }

    @Test
    void periodWrappingFromSaturdayIntoSunday() {
        // Saturday 23:00 → Sunday 12:00 covers Sunday breakfast across the week boundary.
        OpeningHours hours = hours(period(SATURDAY, 23, 0, SUNDAY, 12, 0));
        assertThat(OpeningHoursCheck.isOpenDuring(hours, BREAKFAST, SUNDAY)).isEqualTo(OPEN);
    }

    @Test
    void periodWithoutCloseMeansOpen24Hours() {
        OpeningHours hours = hours(new Period(new Point(0, 0, 0), null));
        assertThat(OpeningHoursCheck.isOpenDuring(hours, BREAKFAST, FRIDAY)).isEqualTo(OPEN);
    }

    @Test
    void missingHoursAreUnknown() {
        assertThat(OpeningHoursCheck.isOpenDuring(null, BREAKFAST, FRIDAY)).isEqualTo(UNKNOWN);
        assertThat(OpeningHoursCheck.isOpenDuring(new OpeningHours(List.of(), List.of()), BREAKFAST, FRIDAY))
                .isEqualTo(UNKNOWN);
    }

    @Test
    void withoutVisitDateAnyDayCounts() {
        OpeningHours hours = hours(period(SATURDAY, 8, 0, SATURDAY, 12, 0));
        assertThat(OpeningHoursCheck.isOpenDuring(hours, BREAKFAST, null)).isEqualTo(OPEN);
        assertThat(OpeningHoursCheck.isOpenDuring(hours, DINNER, null)).isEqualTo(CLOSED);
    }

    private static OpeningHours hours(Period... periods) {
        return new OpeningHours(List.of(periods), List.of());
    }

    private static Period period(int openDay, int openHour, int openMinute, int closeDay, int closeHour, int closeMinute) {
        return new Period(new Point(openDay, openHour, openMinute), new Point(closeDay, closeHour, closeMinute));
    }
}
