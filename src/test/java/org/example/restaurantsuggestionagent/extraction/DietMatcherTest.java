package org.example.restaurantsuggestionagent.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.example.restaurantsuggestionagent.extraction.TestData.dish;
import static org.example.restaurantsuggestionagent.extraction.TestData.query;

import org.example.restaurantsuggestionagent.query.DietPreference;
import org.example.restaurantsuggestionagent.query.DietStrictness;
import org.example.restaurantsuggestionagent.query.TripQuery;
import org.junit.jupiter.api.Test;

class DietMatcherTest {

    private static final MenuItem VEGAN_CURRY = dish("Curry", DietTag.VEGAN, EggContent.NO_EGG);
    private static final MenuItem TORTILLA = dish("Tortilla", DietTag.VEGETARIAN, EggContent.CONTAINS_EGG);
    private static final MenuItem CHEESE_SALAD = dish("Cheese salad", DietTag.VEGETARIAN, EggContent.NO_EGG);
    private static final MenuItem PASTA = dish("Pasta", DietTag.VEGETARIAN, EggContent.UNKNOWN);
    private static final MenuItem CROQUETAS = dish("Croquetas", DietTag.UNCLEAR, EggContent.UNKNOWN);
    private static final MenuItem JAMON = dish("Jamón", DietTag.MEAT_OR_FISH, EggContent.NO_EGG);

    @Test
    void lactoOvoVegetarianGetsVeganAndVegetarianDishesButNotUnclearOnes() {
        TripQuery q = query(DietPreference.VEGETARIAN, DietStrictness.LACTO_OVO);
        assertThat(DietMatcher.matches(VEGAN_CURRY, q)).isTrue();
        assertThat(DietMatcher.matches(TORTILLA, q)).isTrue();
        assertThat(DietMatcher.matches(CROQUETAS, q)).isFalse();
        assertThat(DietMatcher.matches(JAMON, q)).isFalse();
    }

    @Test
    void veganGetsOnlyVeganDishes() {
        TripQuery q = query(DietPreference.VEGETARIAN, DietStrictness.VEGAN);
        assertThat(DietMatcher.matches(VEGAN_CURRY, q)).isTrue();
        assertThat(DietMatcher.matches(CHEESE_SALAD, q)).isFalse();
    }

    @Test
    void noEggNeedsTheMenuToShowThereIsNoEgg() {
        TripQuery q = query(DietPreference.VEGETARIAN, DietStrictness.NO_EGG);
        assertThat(DietMatcher.matches(CHEESE_SALAD, q)).isTrue();
        assertThat(DietMatcher.matches(TORTILLA, q)).isFalse();
        assertThat(DietMatcher.matches(PASTA, q)).isFalse(); // egg unknown: not risked
    }

    @Test
    void nonVegetarianGetsMeatAndFishDishes() {
        TripQuery q = query(DietPreference.NON_VEGETARIAN, DietStrictness.UNSPECIFIED);
        assertThat(DietMatcher.matches(JAMON, q)).isTrue();
        assertThat(DietMatcher.matches(VEGAN_CURRY, q)).isFalse();
    }

    @Test
    void anyDietGetsEverything() {
        TripQuery q = query(DietPreference.ANY, DietStrictness.UNSPECIFIED);
        assertThat(DietMatcher.matches(JAMON, q)).isTrue();
        assertThat(DietMatcher.matches(CROQUETAS, q)).isTrue();
    }

    @Test
    void dishesWithAvoidedFoodsNeverMatch() {
        TripQuery q = query(DietPreference.ANY, DietStrictness.UNSPECIFIED, "mushrooms");
        assertThat(DietMatcher.matches(dish("Risotto", DietTag.VEGETARIAN, EggContent.NO_EGG, "mushrooms"), q))
                .isFalse();
    }
}
