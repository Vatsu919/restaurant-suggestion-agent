package org.example.restaurantsuggestionagent.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.example.restaurantsuggestionagent.agent.SkipReason;
import org.example.restaurantsuggestionagent.discovery.PlaceCandidate;
import org.example.restaurantsuggestionagent.query.Meal;
import org.junit.jupiter.api.Test;

class MenuResolverTest {

    private static final String HOME = "https://resto.example/";

    private final FakeWeb web = new FakeWeb();

    private MenuResolution resolve() {
        MenuResolver resolver = new MenuResolver(web, new RobotsPolicy(web, MenuConfig.AGENT_TOKEN), 8);
        return resolver.resolve(place());
    }

    @Test
    void pdfLinkedFromHomepage() {
        web.html(HOME, "<a href=\"/docs/carta.pdf\">Nuestra carta</a>").pdf("https://resto.example/docs/carta.pdf");

        MenuResolution r = resolve();

        assertThat(r.isFound()).isTrue();
        assertThat(r.source().type()).isEqualTo(MenuType.PDF);
        assertThat(r.source().url()).isEqualTo("https://resto.example/docs/carta.pdf");
        assertThat(r.source().foundVia()).isEqualTo("link “Nuestra carta” on the homepage");
    }

    @Test
    void pdfWithoutPricesIsNotAMenu() {
        // A brochure linked as "La carta" must not be accepted just because it's a PDF.
        web.html(HOME, "<a href=\"/docs/carta.pdf\">La carta</a><a href=\"/menu\">Menu</a>")
                .pdf("https://resto.example/docs/carta.pdf", "Our story", "Since 1998 we have cooked with love",
                        "for the people of Gracia. Come and visit us.")
                .html("https://resto.example/menu", FakeWeb.menuHtml());

        MenuResolution r = resolve();

        assertThat(r.source().url()).isEqualTo("https://resto.example/menu");
        assertThat(r.attempts()).contains("https://resto.example/docs/carta.pdf → PDF has no prices");
    }

    @Test
    void scannedPdfGivesTheImageReason() {
        web.html(HOME, "<p>" + "Welcome to our restaurant. ".repeat(20) + "</p><a href=\"/carta.pdf\">Carta</a>")
                .scannedPdf("https://resto.example/carta.pdf");

        MenuResolution r = resolve();

        assertThat(r.reason()).isEqualTo(SkipReason.MENU_IS_IMAGE);
        assertThat(r.attempts()).contains("https://resto.example/carta.pdf → scanned PDF (images, not read)");
    }

    @Test
    void damagedPdfIsSkippedAndTheSearchContinues() {
        web.html(HOME, "<a href=\"/carta.pdf\">Carta</a><a href=\"/menu\">Menu</a>")
                .pdfBytes("https://resto.example/carta.pdf", "%PDF-1.7 broken".getBytes())
                .html("https://resto.example/menu", FakeWeb.menuHtml());

        MenuResolution r = resolve();

        assertThat(r.source().url()).isEqualTo("https://resto.example/menu");
        assertThat(r.attempts()).contains("https://resto.example/carta.pdf → PDF can't be read");
    }

    @Test
    void websiteAddressThatIsAMenuPdf() {
        web.pdf(HOME);

        MenuResolution r = resolve();

        assertThat(r.source().type()).isEqualTo(MenuType.PDF);
        assertThat(r.source().foundVia()).isEqualTo("the website address itself (a PDF with prices)");
    }

    @Test
    void websiteAddressThatIsAPdfWithoutPrices() {
        web.pdf(HOME, "Hotel Example", "Rooms, spa and conference centre in the heart of Barcelona.");

        MenuResolution r = resolve();

        assertThat(r.reason()).isEqualTo(SkipReason.MENU_NOT_FOUND);
        assertThat(r.detail()).isEqualTo("The website address is a PDF without prices, not a menu");
    }

    @Test
    void htmlMenuPageWithPrices() {
        web.html(HOME, "<a href=\"/menu\">Menu</a>").html("https://resto.example/menu", FakeWeb.menuHtml());

        MenuResolution r = resolve();

        assertThat(r.source().type()).isEqualTo(MenuType.HTML);
        assertThat(r.source().url()).isEqualTo("https://resto.example/menu");
    }

    @Test
    void menusHubPageIsSearchedOneLevelDeeper() {
        web.html(HOME, "<a href=\"/menus\">Menus</a>")
                .html("https://resto.example/menus",
                        "<h1>Our menus</h1><a href=\"/files/brunch.pdf\">Brunch menu</a><a href=\"/files/dinner.pdf\">Dinner menu</a>")
                .pdf("https://resto.example/files/brunch.pdf")
                .pdf("https://resto.example/files/dinner.pdf");

        MenuResolution r = resolve();

        // The restaurant is being considered for breakfast, so the brunch menu is preferred.
        assertThat(r.source().url()).isEqualTo("https://resto.example/files/brunch.pdf");
        assertThat(r.source().foundVia()).isEqualTo("link “Brunch menu” on /menus");
        assertThat(r.attempts()).contains("https://resto.example/menus → page has no prices");
    }

    @Test
    void singlePageSiteWithPricesOnHomepage() {
        web.html(HOME, FakeWeb.menuHtml());

        MenuResolution r = resolve();

        assertThat(r.source().url()).isEqualTo(HOME);
        assertThat(r.source().foundVia()).isEqualTo("the homepage (it lists prices)");
    }

    @Test
    void commonPathWhenHomepageHasNoMenuLink() {
        web.html(HOME, "<p>" + "Welcome to our restaurant. ".repeat(20) + "</p>")
                .html("https://resto.example/carta", FakeWeb.menuHtml());

        MenuResolution r = resolve();

        assertThat(r.source().url()).isEqualTo("https://resto.example/carta");
        assertThat(r.source().foundVia()).isEqualTo("common path /carta");
        assertThat(r.attempts()).contains("https://resto.example/menu → HTTP 404");
    }

    @Test
    void noMenuAnywhereListsWhatWasTried() {
        web.html(HOME, "<p>" + "Welcome to our restaurant. ".repeat(20) + "</p>");

        MenuResolution r = resolve();

        assertThat(r.isFound()).isFalse();
        assertThat(r.reason()).isEqualTo(SkipReason.MENU_NOT_FOUND);
        assertThat(r.detail()).isEqualTo("No menu found on the website");
        assertThat(r.attempts()).startsWith(HOME + " → homepage", "https://resto.example/menu → HTTP 404");
    }

    @Test
    void fetchLimitIsRespected() {
        web.html(HOME, "<p>" + "Welcome to our restaurant. ".repeat(20) + "</p>");

        resolve();

        // Homepage + 7 common paths = the limit of 8; the 8th common path is never fetched.
        assertThat(web.pageFetches()).hasSize(8).doesNotContain("https://resto.example/carta.pdf");
    }

    @Test
    void almostEmptyHomepageSuggestsJavaScript() {
        web.html(HOME, "<div id=\"root\"></div><script src=\"app.js\"></script>");

        MenuResolution r = resolve();

        assertThat(r.detail()).isEqualTo("No menu found; the website seems to need JavaScript to show its content");
    }

    @Test
    void imageLinksAreNotDownloadedAndGiveTheImageReason() {
        // The Veg World case: the menu link points straight at JPG pages.
        web.html(HOME, "<p>" + "Welcome to our restaurant. ".repeat(20) + "</p>"
                + "<a href=\"/uploads/Carta-1.jpg\">Carta</a>");

        MenuResolution r = resolve();

        assertThat(r.isFound()).isFalse();
        assertThat(r.reason()).isEqualTo(SkipReason.MENU_IS_IMAGE);
        assertThat(r.detail()).isEqualTo("The menu is only published as images (on /uploads/Carta-1.jpg); "
                + "image menus aren't read");
        assertThat(web.pageFetches()).doesNotContain("https://resto.example/uploads/Carta-1.jpg");
    }

    @Test
    void linkThatTurnsOutToBeAnImageIsNotAccepted() {
        web.html(HOME, "<p>" + "Welcome to our restaurant. ".repeat(20) + "</p><a href=\"/la-carta\">Carta</a>")
                .jpeg("https://resto.example/la-carta");

        MenuResolution r = resolve();

        assertThat(r.reason()).isEqualTo(SkipReason.MENU_IS_IMAGE);
        assertThat(r.attempts()).contains("https://resto.example/la-carta → image (not read)");
    }

    @Test
    void menuPageShowingMenuImagesGivesTheImageReason() {
        // The Fat Veggies case: /menu shows the menu as <img> tags, one per language.
        web.html(HOME, "<a href=\"/menu\">Menu</a>")
                .html("https://resto.example/menu", "<h1>Menu</h1>"
                        + "<img src=\"https://static.example/media/Menu%20Fat%20Veggies%20-%20Comida%20ING.png\">");

        MenuResolution r = resolve();

        assertThat(r.reason()).isEqualTo(SkipReason.MENU_IS_IMAGE);
        assertThat(r.detail()).isEqualTo("The menu is only published as images (on /menu); image menus aren't read");
    }

    @Test
    void foodPhotosAreNotMistakenForMenuImages() {
        web.html(HOME, "<p>" + "Welcome to our restaurant. ".repeat(20) + "</p>"
                + "<img src=\"/img/brunch-table.jpg\" alt=\"Our brunch\">");

        assertThat(resolve().reason()).isEqualTo(SkipReason.MENU_NOT_FOUND);
    }

    @Test
    void pdfEmbeddedInAScriptOnTheMenuPageIsFound() {
        // The Faire case: /menu opens the PDF from a script, not an <a href>; the name is a hash.
        web.html(HOME, "<a href=\"/menu\">Menu</a>")
                .html("https://resto.example/menu", "<h1>Menu</h1><button>See menu</button><script>"
                        + "window.__data={\"file\":\"https:\\/\\/cdn.example\\/assets\\/69BnTyHWRa.pdf\"}</script>")
                .pdf("https://cdn.example/assets/69BnTyHWRa.pdf");

        MenuResolution r = resolve();

        assertThat(r.isFound()).isTrue();
        assertThat(r.source().type()).isEqualTo(MenuType.PDF);
        assertThat(r.source().url()).isEqualTo("https://cdn.example/assets/69BnTyHWRa.pdf");
        assertThat(r.source().foundVia()).isEqualTo("PDF embedded in /menu");
    }

    @Test
    void embeddedGroupAndAllergenPdfsAreStillExcluded() {
        // The Oassis case: /menu only offers group, student and allergen PDFs.
        web.html(HOME, "<a href=\"/menu\">Menu</a>")
                .html("https://resto.example/menu", "<h1>Menu</h1><script>var files=["
                        + "\"https://resto.example/uploads/Oassis-MenusGrup-Green-CAT.pdf\","
                        + "\"https://resto.example/uploads/Oassis_Menu_Estudiants_CAT.pdf\","
                        + "\"https://resto.example/uploads/Oassis-Alergens-CAT.pdf\"]</script>");

        MenuResolution r = resolve();

        assertThat(r.isFound()).isFalse();
        assertThat(web.pageFetches()).noneMatch(u -> u.contains("/uploads/"));
    }

    @Test
    void unreachableWebsite() {
        web.down(HOME);

        MenuResolution r = resolve();

        assertThat(r.reason()).isEqualTo(SkipReason.WEBSITE_UNREACHABLE);
        assertThat(r.detail()).isEqualTo("The website couldn't be reached (could not connect)");
    }

    @Test
    void robotsTxtBlockingEverything() {
        web.text("https://resto.example/robots.txt", "User-agent: *\nDisallow: /").html(HOME, FakeWeb.menuHtml());

        MenuResolution r = resolve();

        assertThat(r.reason()).isEqualTo(SkipReason.BLOCKED_BY_ROBOTS);
        assertThat(web.pageFetches()).isEmpty();
    }

    @Test
    void robotsTxtBlockingOnePathSkipsOnlyThatPath() {
        web.text("https://resto.example/robots.txt", "User-agent: *\nDisallow: /menu")
                .html(HOME, "<a href=\"/menu\">Menu</a><a href=\"/carta\">Carta</a>")
                .html("https://resto.example/menu", FakeWeb.menuHtml())
                .html("https://resto.example/carta", FakeWeb.menuHtml());

        MenuResolution r = resolve();

        assertThat(r.source().url()).isEqualTo("https://resto.example/carta");
        assertThat(r.attempts()).contains("https://resto.example/menu → blocked by robots.txt");
        assertThat(web.pageFetches()).doesNotContain("https://resto.example/menu");
    }

    private static PlaceCandidate place() {
        return new PlaceCandidate("p1", "Resto", null, HOME, null, 4.5, 100, null, "OPERATIONAL", null,
                null, null, null, null, null, List.of(), Set.of(Meal.BREAKFAST), List.of());
    }
}
