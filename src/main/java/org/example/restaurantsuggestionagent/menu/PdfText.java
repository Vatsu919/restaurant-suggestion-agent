package org.example.restaurantsuggestionagent.menu;

import java.io.IOException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/** Extracts the text layer of a PDF with PDFBox. Scanned PDFs have no text layer and give "". */
final class PdfText {

    /** Menus are a few pages; this bounds the work on a large brochure. */
    static final int MAX_PAGES = 30;

    private PdfText() {
    }

    /** @throws IOException if the file is damaged or password-protected */
    static String extract(byte[] pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setEndPage(MAX_PAGES);
            return stripper.getText(doc);
        }
    }
}
