package org.example.restaurantsuggestionagent.filter;

import java.util.EnumMap;
import java.util.Map;

import org.example.restaurantsuggestionagent.query.Meal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Typical European meal hours, used to check opening hours. Configurable per deployment. */
@Component
public class MealWindows {

    private final Map<Meal, TimeWindow> windows = new EnumMap<>(Meal.class);

    public MealWindows(@Value("${filter.meal-hours.breakfast:07:00-11:00}") String breakfast,
                       @Value("${filter.meal-hours.lunch:12:00-15:00}") String lunch,
                       @Value("${filter.meal-hours.dinner:18:00-22:00}") String dinner) {
        windows.put(Meal.BREAKFAST, TimeWindow.parse(breakfast));
        windows.put(Meal.LUNCH, TimeWindow.parse(lunch));
        windows.put(Meal.DINNER, TimeWindow.parse(dinner));
    }

    public TimeWindow of(Meal meal) {
        return windows.get(meal);
    }
}
