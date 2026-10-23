package org.example.restaurantsuggestionagent.menu;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RobotsRulesTest {

    private static final String AGENT = "RestaurantSuggestionAgent";

    @Test
    void disallowAllBlocksEverything() {
        RobotsRules rules = RobotsRules.parse("User-agent: *\nDisallow: /", AGENT);
        assertThat(rules.allows("/")).isFalse();
        assertThat(rules.allows("/menu")).isFalse();
    }

    @Test
    void emptyDisallowAllowsEverything() {
        RobotsRules rules = RobotsRules.parse("User-agent: *\nDisallow:", AGENT);
        assertThat(rules.allows("/menu")).isTrue();
    }

    @Test
    void longestMatchWins() {
        RobotsRules rules = RobotsRules.parse("""
                User-agent: *
                Disallow: /private
                Allow: /private/menu
                """, AGENT);
        assertThat(rules.allows("/private/admin")).isFalse();
        assertThat(rules.allows("/private/menu.pdf")).isTrue();
        assertThat(rules.allows("/carta")).isTrue();
    }

    @Test
    void ourOwnGroupReplacesTheStarGroup() {
        RobotsRules rules = RobotsRules.parse("""
                User-agent: *
                Disallow: /

                User-agent: RestaurantSuggestionAgent
                Disallow: /admin
                """, AGENT);
        assertThat(rules.allows("/menu")).isTrue();
        assertThat(rules.allows("/admin")).isFalse();
    }

    @Test
    void consecutiveUserAgentLinesShareOneGroup() {
        RobotsRules rules = RobotsRules.parse("""
                User-agent: Googlebot
                User-agent: *
                Disallow: /wp-admin
                """, AGENT);
        assertThat(rules.allows("/wp-admin/x")).isFalse();
    }

    @Test
    void wildcardsAndEndAnchor() {
        RobotsRules rules = RobotsRules.parse("""
                User-agent: *
                Disallow: /*.pdf$
                Disallow: /*?session=
                """, AGENT);
        assertThat(rules.allows("/files/carta.pdf")).isFalse();
        assertThat(rules.allows("/files/carta.pdf.html")).isTrue();
        assertThat(rules.allows("/menu?session=1")).isFalse();
    }

    @Test
    void commentsAndOtherDirectivesAreIgnored() {
        RobotsRules rules = RobotsRules.parse("""
                # our rules
                User-agent: *   # everyone
                Crawl-delay: 10
                Sitemap: https://example.com/sitemap.xml
                Disallow: /tmp  # temp files
                """, AGENT);
        assertThat(rules.allows("/tmp/a")).isFalse();
        assertThat(rules.allows("/menu")).isTrue();
    }
}
