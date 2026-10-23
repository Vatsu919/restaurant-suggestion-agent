package org.example.restaurantsuggestionagent.menu;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Answers "may we fetch this URL?" from the site's robots.txt, fetched once per site
 * and cached. A missing or unreachable robots.txt allows everything.
 */
public class RobotsPolicy {

    private final PageFetcher fetcher;
    private final String agentToken;
    private final Map<String, RobotsRules> bySite = new ConcurrentHashMap<>();

    /** @param agentToken the product name in our User-Agent, e.g. "RestaurantSuggestionAgent" */
    public RobotsPolicy(PageFetcher fetcher, String agentToken) {
        this.fetcher = fetcher;
        this.agentToken = agentToken;
    }

    public boolean allows(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (uri.getScheme() == null || uri.getHost() == null) {
            return false;
        }
        String site = uri.getScheme() + "://" + uri.getHost() + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
        String path = (uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath())
                + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
        return bySite.computeIfAbsent(site, this::load).allows(path);
    }

    private RobotsRules load(String site) {
        try {
            FetchedPage page = fetcher.fetch(site + "/robots.txt");
            if (!page.ok()) {
                return RobotsRules.ALLOW_ALL;
            }
            return RobotsRules.parse(new String(page.body(), StandardCharsets.UTF_8), agentToken);
        } catch (IOException e) {
            return RobotsRules.ALLOW_ALL;
        }
    }
}
