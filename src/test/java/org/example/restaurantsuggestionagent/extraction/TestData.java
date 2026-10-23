package org.example.restaurantsuggestionagent.extraction;

import java.util.List;

import org.example.restaurantsuggestionagent.query.Budget;
import org.example.restaurantsuggestionagent.query.DietPreference;
import org.example.restaurantsuggestionagent.query.DietStrictness;
import org.example.restaurantsuggestionagent.query.Meal;
import org.example.restaurantsuggestionagent.query.TripQuery;

final class TestData {

    private TestData() {
    }

    static TripQuery query(DietPreference diet, DietStrictness strictness, String... avoid) {
        return new TripQuery("Barcelona", "", List.of(Meal.DINNER), diet, strictness, Budget.UNSPECIFIED,
                List.of(), List.of(avoid), "", "en", 0, List.of());
    }

    static TripQuery vegetarian() {
        return query(DietPreference.VEGETARIAN, DietStrictness.LACTO_OVO);
    }

    static MenuItem dish(String name, DietTag diet, EggContent egg) {
        return dish(name, diet, egg, "");
    }

    static MenuItem dish(String name, DietTag diet, EggContent egg, List<Meal> meals) {
        return new MenuItem(name, name, "", "Mains", meals, "9,50 €", 9.5, diet, egg, "", false);
    }

    static MenuItem dish(String name, DietTag diet, EggContent egg, String containsAvoided) {
        return new MenuItem(name, name, "", "Mains", List.of(), "9,50 €", 9.5, diet, egg, containsAvoided, false);
    }
}
