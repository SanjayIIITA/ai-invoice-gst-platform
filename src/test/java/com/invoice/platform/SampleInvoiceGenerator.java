package com.invoice.platform;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.File;

public class SampleInvoiceGenerator {

    public static void main(String[] args) throws Exception {
        PDType1Font bold    = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {

                // Title
                cs.beginText();
                cs.setFont(bold, 20);
                cs.newLineAtOffset(200, 750);
                cs.showText("TAX INVOICE");
                cs.endText();

                // Supplier details
                line(cs, bold, regular, 50, 710, "Supplier Name  :", "ABC Technologies Pvt Ltd");
                line(cs, bold, regular, 50, 690, "Supplier GSTIN :", "29ABCDE1234F1Z5");
                line(cs, bold, regular, 50, 670, "Address        :", "123 Tech Park, Bangalore, Karnataka - 560001");
                line(cs, bold, regular, 50, 650, "Email          :", "billing@abctech.com");

                // Divider
                cs.moveTo(50, 635);
                cs.lineTo(550, 635);
                cs.stroke();

                // Invoice details
                line(cs, bold, regular, 50, 615, "Invoice Number :", "INV-2024-001");
                line(cs, bold, regular, 50, 595, "Invoice Date   :", "2024-10-15");
                line(cs, bold, regular, 50, 575, "Due Date       :", "2024-11-15");

                // Divider
                cs.moveTo(50, 560);
                cs.lineTo(550, 560);
                cs.stroke();

                // Buyer details
                line(cs, bold, regular, 50, 540, "Bill To        :", "XYZ Enterprises Pvt Ltd");
                line(cs, bold, regular, 50, 520, "Buyer GSTIN    :", "27XYZAB5678C1Z3");
                line(cs, bold, regular, 50, 500, "Address        :", "456 Business Hub, Mumbai, Maharashtra - 400001");

                // Divider
                cs.moveTo(50, 485);
                cs.lineTo(550, 485);
                cs.stroke();

                // Line items header
                cs.beginText();
                cs.setFont(bold, 11);
                cs.newLineAtOffset(50, 465);
                cs.showText("Description");
                cs.endText();

                cs.beginText();
                cs.setFont(bold, 11);
                cs.newLineAtOffset(280, 465);
                cs.showText("Qty");
                cs.endText();

                cs.beginText();
                cs.setFont(bold, 11);
                cs.newLineAtOffset(330, 465);
                cs.showText("Unit Price");
                cs.endText();

                cs.beginText();
                cs.setFont(bold, 11);
                cs.newLineAtOffset(440, 465);
                cs.showText("Amount");
                cs.endText();

                cs.moveTo(50, 455);
                cs.lineTo(550, 455);
                cs.stroke();

                // Line items
                lineItem(cs, regular, 435, "Software Development Services", "1", "8,000.00", "8,000.00");
                lineItem(cs, regular, 415, "Cloud Infrastructure Setup",    "1", "1,500.00", "1,500.00");
                lineItem(cs, regular, 395, "Technical Support (Monthly)",   "1",   "500.00",   "500.00");

                cs.moveTo(50, 380);
                cs.lineTo(550, 380);
                cs.stroke();

                // Totals
                line(cs, bold, regular, 350, 360, "Taxable Amount :", "Rs. 10,000.00");
                line(cs, bold, regular, 350, 340, "CGST (9%)      :", "Rs.    900.00");
                line(cs, bold, regular, 350, 320, "SGST (9%)      :", "Rs.    900.00");
                line(cs, bold, regular, 350, 300, "GST Amount     :", "Rs.  1,800.00");

                cs.moveTo(350, 288);
                cs.lineTo(550, 288);
                cs.stroke();

                line(cs, bold, bold,    350, 270, "Total Amount   :", "Rs. 11,800.00");

                // Footer
                cs.moveTo(50, 200);
                cs.lineTo(550, 200);
                cs.stroke();

                cs.beginText();
                cs.setFont(regular, 9);
                cs.newLineAtOffset(50, 185);
                cs.showText("Bank: HDFC Bank  |  Account: 1234567890  |  IFSC: HDFC0001234");
                cs.endText();

                cs.beginText();
                cs.setFont(regular, 9);
                cs.newLineAtOffset(50, 170);
                cs.showText("This is a computer generated invoice. No signature required.");
                cs.endText();
            }

            File output = new File("samples/sample-invoice.pdf");
            doc.save(output);
            System.out.println("Sample invoice created: " + output.getAbsolutePath());
        }
    }

    private static void line(PDPageContentStream cs, PDType1Font labelFont, PDType1Font valueFont,
                              float x, float y, String label, String value) throws Exception {
        cs.beginText();
        cs.setFont(labelFont, 11);
        cs.newLineAtOffset(x, y);
        cs.showText(label);
        cs.endText();

        cs.beginText();
        cs.setFont(valueFont, 11);
        cs.newLineAtOffset(x + 130, y);
        cs.showText(value);
        cs.endText();
    }

    private static void lineItem(PDPageContentStream cs, PDType1Font font,
                                  float y, String desc, String qty, String price, String amount) throws Exception {
        cs.beginText(); cs.setFont(font, 10); cs.newLineAtOffset(50,  y); cs.showText(desc);   cs.endText();
        cs.beginText(); cs.setFont(font, 10); cs.newLineAtOffset(285, y); cs.showText(qty);    cs.endText();
        cs.beginText(); cs.setFont(font, 10); cs.newLineAtOffset(330, y); cs.showText(price);  cs.endText();
        cs.beginText(); cs.setFont(font, 10); cs.newLineAtOffset(440, y); cs.showText(amount); cs.endText();
    }
}
