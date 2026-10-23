package org.example.restaurantsuggestionagent.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.example.restaurantsuggestionagent.query.Meal;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

class MenuLinkFinderTest {

    private static List<String> urls(String html, Set<Meal> meals) {
        Document doc = Jsoup.parse(html, "https://resto.example/");
        return MenuLinkFinder.find(doc, meals).stream().map(MenuLinkFinder.Link::url).toList();
    }

    @Test
    void findsMenuLinksInSeveralLanguagesAndIgnoresTheRest() {
        String html = """
                <a href="/carta">La Carta</a>
                <a href="/speisekarte">Speisekarte</a>
                <a href="/nosotros">Sobre nosotros</a>
                <a href="/contacto">Contacto</a>
                """;
        assertThat(urls(html, Set.of())).containsExactly(
                "https://resto.example/carta", "https://resto.example/speisekarte");
    }

    @Test
    void accentsAndCaseDoNotMatter() {
        assertThat(urls("<a href=\"/p1\">MENÚ</a>", Set.of())).containsExactly("https://resto.example/p1");
    }

    @Test
    void wineListsReservationsAndAllergensAreExcluded() {
        String html = """
                <a href="/carta-vinos">Carta de vinos</a>
                <a href="/reservas">Reservar mesa</a>
                <a href="/alergenos.pdf">Carta de alérgenos</a>
                <a href="/carta">Carta</a>
                """;
        assertThat(urls(html, Set.of())).containsExactly("https://resto.example/carta");
    }

    @Test
    void allergenChartIsExcludedEvenWithMenuWordsInItsFileName() {
        // The Marmot case: two menu words + PDF used to outweigh one "alérgenos".
        String html = "<a href=\"/uploads/menu-2025-carta-alergenos.pdf\">Consulta la carta de alérgenos</a>";
        assertThat(urls(html, Set.of())).isEmpty();
    }

    @Test
    void groupAndStudentMenusAreExcluded() {
        // The Oassis case.
        String html = """
                <a href="/menu-per-a-grups/">Menú per a grups</a>
                <a href="/uploads/Menu_Estudiants.pdf">Student 17,50€</a>
                <a href="/carta">Carta</a>
                """;
        assertThat(urls(html, Set.of())).containsExactly("https://resto.example/carta");
    }

    @Test
    void pdfMenusRankAboveHtmlMenus() {
        String html = """
                <a href="/menu">Menu</a>
                <a href="/files/menu-2026.pdf">Menu</a>
                """;
        assertThat(urls(html, Set.of())).first().isEqualTo("https://resto.example/files/menu-2026.pdf");
    }

    @Test
    void menuForTheRestaurantsMealRanksFirst() {
        String html = """
                <a href="/dinner">Dinner menu</a>
                <a href="/brunch">Brunch menu</a>
                """;
        assertThat(urls(html, Set.of(Meal.BREAKFAST))).first().isEqualTo("https://resto.example/brunch");
        assertThat(urls(html, Set.of(Meal.DINNER))).first().isEqualTo("https://resto.example/dinner");
    }

    @Test
    void urlPathAndImageAltCount() {
        String html = """
                <a href="/files/carta.pdf">Descargar</a>
                <a href="/p2"><img src="x.png" alt="Our menu"></a>
                """;
        assertThat(urls(html, Set.of())).containsExactly(
                "https://resto.example/files/carta.pdf", "https://resto.example/p2");
    }

    @Test
    void socialMediaAndDeliveryAppsAreExcludedButOtherHostsAreNot() {
        String html = """
                <a href="https://www.instagram.com/resto/">Menu on Instagram</a>
                <a href="https://glovoapp.com/es/es/barcelona/resto/">Order menu</a>
                <a href="https://www.tripadvisor.es/Restaurant_Review">Menu</a>
                <a href="https://cdn.filehost.example/resto/carta.pdf">Carta</a>
                """;
        assertThat(urls(html, Set.of())).containsExactly("https://cdn.filehost.example/resto/carta.pdf");
    }

    @Test
    void linksBackToTheSamePageAreIgnored() {
        assertThat(urls("<a href=\"#menu\">Menu</a><a href=\"/\">Menu</a>", Set.of())).isEmpty();
    }

    @Test
    void wholeWordsOnly() {
        // "eat" must not match inside "theatre".
        assertThat(urls("<a href=\"/theatre\">Theatre nights</a>", Set.of())).isEmpty();
    }

    @Test
    void looksLikeMenuNeedsSeveralPrices() {
        assertThat(MenuText.looksLikeMenu(FakeWeb.menuHtml())).isTrue();
        assertThat(MenuText.looksLikeMenu("Brunch from 9:00. Menu del dia 14,50 €. Call us!")).isFalse();
        assertThat(MenuText.looksLikeMenu("Tapas 4.50 5.00 6.50 7.00 8.50 9.00 10.50 12.00")).isTrue();
    }
}
