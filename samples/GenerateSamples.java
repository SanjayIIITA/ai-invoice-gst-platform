///usr/bin/env java --source 21 "$0" "$@"; exit $?

import java.io.*;
import java.nio.file.*;

/**
 * Generates multiple sample GST invoices as simple text-based PDFs.
 * Run: java samples/GenerateSamples.java
 *
 * Uses only JDK — no external dependencies.
 * Produces minimal but valid PDFs that PDFBox can extract text from.
 */
public class GenerateSamples {

    record Invoice(String number, String supplierName, String gstin,
                   String date, int taxable, int gst, int total, String gstRate) {}

    static final Invoice[] INVOICES = {
        new Invoice("INV-2024-001", "ABC Technologies Pvt Ltd",  "29ABCDE1234F1Z5", "2024-10-15", 10000, 1800, 11800, "18%"),
        new Invoice("INV-2024-002", "XYZ Supplies Pvt Ltd",      "27XYZAB5678G1Z3", "2024-11-20",  5000,  900,  5900, "18%"),
        new Invoice("INV-2024-003", "PQR Services Ltd",          "07PQRCD9012H1Z1", "2024-12-05", 20000, 2400, 22400, "12%"),
        new Invoice("INV-2025-001", "LMN Traders",               "33LMNEF3456I1Z8", "2025-01-10",  8000,  400,  8400,  "5%"),
        new Invoice("INV-2025-002", "DEF Consulting Pvt Ltd",    "19DEFGH7890J1Z6", "2025-03-22", 15000, 2700, 17700, "18%"),
    };

    public static void main(String[] args) throws Exception {
        Path dir = Path.of("samples");
        for (Invoice inv : INVOICES) {
            byte[] pdf = buildPdf(inv);
            Path out = dir.resolve(inv.number() + ".pdf");
            Files.write(out, pdf);
            System.out.println("Generated: " + out);
        }
    }

    static byte[] buildPdf(Invoice inv) throws Exception {
        String text = buildInvoiceText(inv);
        return buildMinimalPdf(text);
    }

    static String buildInvoiceText(Invoice inv) {
        return String.join("\n",
            "================================================================================",
            "                          TAX INVOICE",
            "================================================================================",
            "",
            "Supplier: " + inv.supplierName(),
            "GSTIN:    " + inv.gstin(),
            "",
            "Invoice No: " + inv.number(),
            "Date:       " + inv.date(),
            "",
            "--------------------------------------------------------------------------------",
            "Description                          Qty    Rate       Amount",
            "--------------------------------------------------------------------------------",
            "Professional Services                  1  " + inv.taxable() + ".00   " + inv.taxable() + ".00",
            "--------------------------------------------------------------------------------",
            "",
            "Taxable Amount:  INR " + inv.taxable() + ".00",
            "GST (" + inv.gstRate() + "):         INR " + inv.gst() + ".00",
            "Total Amount:    INR " + inv.total() + ".00",
            "",
            "================================================================================",
            "This is a computer generated invoice. No signature required.",
            "================================================================================"
        );
    }

    static byte[] buildMinimalPdf(String text) throws Exception {
        // Escape special PDF characters
        String escaped = text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");

        // Split into lines for PDF text rendering
        String[] lines = escaped.split("\n");
        StringBuilder stream = new StringBuilder();
        stream.append("BT\n/F1 10 Tf\n50 750 Td\n12 TL\n");
        for (String line : lines) {
            stream.append("(").append(line).append(") Tj T*\n");
        }
        stream.append("ET\n");

        String streamContent = stream.toString();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // PDF structure
        String header = "%PDF-1.4\n";
        String catalog = "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n";
        String pages   = "2 0 obj\n<< /Type /Pages /Kids [4 0 R] /Count 1 >>\nendobj\n";
        String font    = "3 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Courier >>\nendobj\n";
        String page    = "4 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                       + "/Contents 5 0 R /Resources << /Font << /F1 3 0 R >> >> >>\nendobj\n";
        String streamObj = "5 0 obj\n<< /Length " + streamContent.length() + " >>\nstream\n"
                         + streamContent + "endstream\nendobj\n";

        baos.write(header.getBytes());
        int[] offsets = new int[6];
        offsets[1] = baos.size(); baos.write(catalog.getBytes());
        offsets[2] = baos.size(); baos.write(pages.getBytes());
        offsets[3] = baos.size(); baos.write(font.getBytes());
        offsets[4] = baos.size(); baos.write(page.getBytes());
        offsets[5] = baos.size(); baos.write(streamObj.getBytes());

        int xrefOffset = baos.size();
        StringBuilder xref = new StringBuilder("xref\n0 6\n0000000000 65535 f \n");
        for (int i = 1; i <= 5; i++) {
            xref.append(String.format("%010d 00000 n \n", offsets[i]));
        }
        xref.append("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n")
            .append(xrefOffset).append("\n%%EOF\n");
        baos.write(xref.toString().getBytes());

        return baos.toByteArray();
    }
}
