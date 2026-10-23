package org.example.restaurantsuggestionagent.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.example.restaurantsuggestionagent.query.Budget;
import org.example.restaurantsuggestionagent.query.DietPreference;
import org.example.restaurantsuggestionagent.query.DietStrictness;
import org.example.restaurantsuggestionagent.query.Meal;
import org.example.restaurantsuggestionagent.query.TripQuery;
import org.junit.jupiter.api.Test;

class SearchQueryBuilderTest {

    @Test
    void vegetarianWithNeighbourhoodAndTwoMeals() {
        TripQuery q = query("Gràcia", DietPreference.VEGETARIAN, DietStrictness.LACTO_OVO, List.of(),
                List.of(Meal.BREAKFAST, Meal.DINNER));
        assertThat(SearchQueryBuilder.build(q)).isEqualTo("vegetarian breakfast and dinner in Gràcia, Barcelona");
    }

    @Test
    void veganSaysVegan() {
        TripQuery q = query("", DietPreference.VEGETARIAN, DietStrictness.VEGAN, List.of(), List.of(Meal.LUNCH));
        assertThat(SearchQueryBuilder.build(q)).isEqualTo("vegan lunch in Barcelona");
    }

    @Test
    void vegetarianWithCuisine() {
        TripQuery q = query("", DietPreference.VEGETARIAN, DietStrictness.LACTO_OVO, List.of("tapas"),
                List.of(Meal.DINNER));
        assertThat(SearchQueryBuilder.build(q)).isEqualTo("vegetarian tapas dinner in Barcelona");
    }

    @Test
    void nonVegetarianUsesCuisine() {
        TripQuery q = query("", DietPreference.NON_VEGETARIAN, DietStrictness.UNSPECIFIED, List.of("seafood"),
                List.of(Meal.DINNER));
        assertThat(SearchQueryBuilder.build(q)).isEqualTo("seafood dinner in Barcelona");
    }

    @Test
    void anyDietWithoutCuisineAndAllMeals() {
        TripQuery q = query("", DietPreference.ANY, DietStrictness.UNSPECIFIED, List.of(), List.of(Meal.values()));
        assertThat(SearchQueryBuilder.build(q)).isEqualTo("restaurant for breakfast, lunch and dinner in Barcelona");
    }

    @Test
    void tastePreferencesStayOutOfTheSearch() {
        TripQuery q = new TripQuery("Barcelona", "", List.of(Meal.DINNER), DietPreference.VEGETARIAN,
                DietStrictness.LACTO_OVO, Budget.UNSPECIFIED, List.of(), List.of(), "", "en", 0, List.of(),
                List.of("mild, not spicy"));
        assertThat(SearchQueryBuilder.build(q)).isEqualTo("vegetarian dinner in Barcelona");
    }

    private static TripQuery query(String neighbourhood, DietPreference diet, DietStrictness strictness,
                                   List<String> cuisines, List<Meal> meals) {
        return new TripQuery("Barcelona", neighbourhood, meals, diet, strictness,
                Budget.UNSPECIFIED, cuisines, List.of(), "", "en", 0, List.of(), List.of());
    }
}
