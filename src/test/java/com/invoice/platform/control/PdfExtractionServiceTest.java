package com.invoice.platform.control;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfExtractionServiceTest {

    private final PdfExtractionService service = new PdfExtractionService();

    @TempDir
    Path tempDir;

    @Test
    void extractText_returnsTextFromValidPdf() throws IOException {
        Path pdf = tempDir.resolve("invoice.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(100, 700);
                stream.showText("Invoice Number: INV-2024-001");
                stream.endText();
            }
            doc.save(pdf.toFile());
        }

        String text = service.extractText(pdf);

        assertThat(text).contains("INV-2024-001");
    }

    @Test
    void extractText_returnsEmptyForPdfWithNoPages() throws IOException {
        Path pdf = tempDir.resolve("empty.pdf");
        try (PDDocument doc = new PDDocument()) {
            doc.save(pdf.toFile());
        }

        String text = service.extractText(pdf);

        assertThat(text).isEmpty();
    }

    @Test
    void extractText_throwsIOExceptionForCorruptedFile() throws IOException {
        Path corrupted = tempDir.resolve("corrupted.pdf");
        Files.writeString(corrupted, "this is not a pdf");

        assertThatThrownBy(() -> service.extractText(corrupted))
                .isInstanceOf(IOException.class);
    }
}
