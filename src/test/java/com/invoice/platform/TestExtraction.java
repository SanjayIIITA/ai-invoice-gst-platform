package com.invoice.platform;

import com.invoice.platform.control.PdfExtractionService;
import java.nio.file.Paths;

public class TestExtraction {
    public static void main(String[] args) throws Exception {
        PdfExtractionService service = new PdfExtractionService();
        String text = service.extractText(
            Paths.get("uploads/5afc6027-e4db-48f9-81d2-e9a92db2688c.pdf"));
        System.out.println("=== EXTRACTED TEXT ===");
        System.out.println(text);
        System.out.println("=== LENGTH: " + text.length() + " ===");
    }
}
