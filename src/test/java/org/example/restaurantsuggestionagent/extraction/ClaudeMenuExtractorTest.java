package org.example.restaurantsuggestionagent.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.example.restaurantsuggestionagent.query.DietPreference;
import org.example.restaurantsuggestionagent.query.DietStrictness;
import org.junit.jupiter.api.Test;

/** Runs offline: checks the request we would send, without calling the API. */
class ClaudeMenuExtractorTest {

    @Test
    void requestCarriesTheMenuTheUsersDietAndTheSchema() {
        ClaudeMenuExtractor extractor = new ClaudeMenuExtractor(null, "claude-opus-5-5");

        String request = extractor.buildRequest("Teresa Carles", MenuInput.ofText("Hummus 8,50 €"),
                TestData.query(DietPreference.VEGETARIAN, DietStrictness.NO_EGG, "nuts")).rawParams().toString();

        assertThat(request)
                .contains("claude-opus-5-5")
                .contains("Restaurant: Teresa Carles", "VEGETARIAN (NO_EGG)", "avoid: nuts")
                .contains("<menu>", "Hummus 8,50 €")
                .contains("menuKind", "GROUP_OR_EVENT", "translatedName", "MEAT_OR_FISH", "CONTAINS_EGG")
                .contains("without naming the dishes is DAILY_SPECIALS", "shop or product page, is NOT_A_MENU")
                .doesNotContain("application/pdf");
    }

    @Test
    void pdfMenusAreSentAsADocumentNotAsText() {
        ClaudeMenuExtractor extractor = new ClaudeMenuExtractor(null, "claude-opus-5-5");
        byte[] pdf = "%PDF-1.7 menu".getBytes(StandardCharsets.US_ASCII);

        String request = extractor.buildRequest("Faire", MenuInput.ofPdf(pdf), TestData.vegetarian())
                .rawParams().toString();

        assertThat(request)
                .contains("application/pdf", Base64.getEncoder().encodeToString(pdf), "Faire menu")
                .contains("The menu is the attached PDF.")
                .doesNotContain("<menu>");
    }
}
