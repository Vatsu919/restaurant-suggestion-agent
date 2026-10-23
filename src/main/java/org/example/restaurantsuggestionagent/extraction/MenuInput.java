package org.example.restaurantsuggestionagent.extraction;

/**
 * What the LLM is given to read: the text of an HTML menu (extracted in Java), or a
 * PDF menu as it is, because a PDF's layout (prices in their own column, several
 * columns per page) is lost when it is turned into text.
 */
public record MenuInput(String text, byte[] pdf) {

    public static MenuInput ofText(String text) {
        return new MenuInput(text, null);
    }

    public static MenuInput ofPdf(byte[] pdf) {
        return new MenuInput(null, pdf);
    }

    public boolean isPdf() {
        return pdf != null;
    }

    @Override
    public String toString() {
        return isPdf() ? "MenuInput[PDF, " + pdf.length + " bytes]" : "MenuInput[text, " + text.length() + " chars]";
    }
}
