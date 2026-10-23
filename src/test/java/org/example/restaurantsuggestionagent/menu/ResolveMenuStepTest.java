package org.example.restaurantsuggestionagent.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.example.restaurantsuggestionagent.agent.RunContext;
import org.example.restaurantsuggestionagent.agent.SkipReason;
import org.example.restaurantsuggestionagent.agent.SkippedRestaurant;
import org.example.restaurantsuggestionagent.discovery.PlaceCandidate;
import org.example.restaurantsuggestionagent.events.AgentEvent;
import org.example.restaurantsuggestionagent.events.RunEventPublisher;
import org.example.restaurantsuggestionagent.query.Meal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResolveMenuStepTest {

    private final RunEventPublisher events = new RunEventPublisher();
    private final FakeWeb web = new FakeWeb();
    private RunContext context;

    @BeforeEach
    void setUp() {
        UUID runId = UUID.randomUUID();
        events.open(runId);
        context = new RunContext(runId, "veg dinner", events);
    }

    private ResolveMenuStep step() {
        MenuResolver resolver = new MenuResolver(web, new RobotsPolicy(web, MenuConfig.AGENT_TOKEN), 8);
        return new ResolveMenuStep(resolver, Runnable::run);
    }

    @Test
    void keepsRestaurantsWithMenusAndSkipsTheRestWithWhatWasTried() {
        web.html("https://a.example/", "<a href=\"/carta.pdf\">Carta</a>").pdf("https://a.example/carta.pdf")
                .html("https://b.example/", "<p>" + "Welcome to our restaurant. ".repeat(20) + "</p>")
                .down("https://c.example/");
        context.candidates(List.of(place("a"), place("b"), place("c")));

        step().execute(context);

        assertThat(context.candidates()).extracting(PlaceCandidate::id).containsExactly("a");
        assertThat(context.menus()).containsOnlyKeys("a");
        assertThat(context.menus().get("a").type()).isEqualTo(MenuType.PDF);

        assertThat(context.skippedRestaurants()).extracting(SkippedRestaurant::id, SkippedRestaurant::reason)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("b", SkipReason.MENU_NOT_FOUND),
                        org.assertj.core.groups.Tuple.tuple("c", SkipReason.WEBSITE_UNREACHABLE));
        assertThat(context.skippedRestaurants().get(0).detail())
                .startsWith("No menu found on the website (checked: https://b.example/ → homepage, "
                        + "https://b.example/menu → HTTP 404")
                .endsWith("and 3 more)");

        List<String> messages = events.events(context.runId()).stream().map(AgentEvent::message).toList();
        assertThat(messages).contains(
                "Name a: menu found (PDF) via link “Carta” on the homepage → https://a.example/carta.pdf");
        assertThat(messages).last().asString().startsWith("Found menus for 1 of 3 restaurants {PDF=1}; skipped 2");
    }

    @Test
    void noMenusAtAllFailsTheRun() {
        web.down("https://a.example/");
        context.candidates(List.of(place("a")));

        assertThatThrownBy(() -> step().execute(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No menu could be found for any of the 1 restaurants");
    }

    private static PlaceCandidate place(String id) {
        return new PlaceCandidate(id, "Name " + id, null, "https://" + id + ".example/", null, 4.5, 100, null,
                "OPERATIONAL", null, null, null, null, null, null, List.of(), Set.of(Meal.DINNER), List.of());
    }
}
