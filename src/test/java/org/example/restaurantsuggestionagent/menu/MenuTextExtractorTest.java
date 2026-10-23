package org.example.restaurantsuggestionagent.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class MenuTextExtractorTest {

    @Test
    void htmlKeepsOneDishPerLineAndDropsScriptsAndNavigation() throws Exception {
        String html = "<html><head><style>p{color:red}</style></head><body>"
                + "<nav><a href=\"/\">Inici</a> <a href=\"/carta\">Carta</a></nav>"
                + "<h2>Entrants</h2><ul><li>Hummus <b>8,50 €</b></li><li>Croquetes de bolets 9 €</li></ul>"
                + "<p>Pa amb tomàquet<br>3,50 €</p>"
                + "<table><tr><td>Curry</td><td>13,00 €</td></tr></table>"
                + "<script>gtag(\"config\")</script><footer>Carrer Major 1</footer></body></html>";

        String text = MenuTextExtractor.htmlText(html(html));

        assertThat(text).isEqualTo("""
                Entrants
                Hummus 8,50 €
                Croquetes de bolets 9 €
                Pa amb tomàquet
                3,50 €
                Curry 13,00 €
                """);
    }

    private static MenuSource html(String html) {
        return new MenuSource("https://a.example/carta", MenuType.HTML, "text/html",
                html.getBytes(StandardCharsets.UTF_8), "test");
    }
}
