package org.example.restaurantsuggestionagent.menu;

import java.util.List;

import org.example.restaurantsuggestionagent.agent.SkipReason;

/**
 * Outcome of looking for one restaurant's menu: either {@code source} is set, or
 * {@code reason} and {@code detail} say why not.
 *
 * @param attempts every URL tried and what happened, e.g. "/menu → HTTP 404"
 */
public record MenuResolution(MenuSource source, SkipReason reason, String detail, List<String> attempts) {

    static MenuResolution found(MenuSource source, List<String> attempts) {
        return new MenuResolution(source, null, null, List.copyOf(attempts));
    }

    static MenuResolution notFound(SkipReason reason, String detail, List<String> attempts) {
        return new MenuResolution(null, reason, detail, List.copyOf(attempts));
    }

    public boolean isFound() {
        return source != null;
    }
}
