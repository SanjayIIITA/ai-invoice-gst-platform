package com.invoice.platform.control;

import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.invoice.platform.boundary.InvoiceExtractionResult;
import com.invoice.platform.boundary.InvoiceResponse;
import com.invoice.platform.entity.ExtractionRun;
import com.invoice.platform.entity.Invoice;
import com.invoice.platform.entity.InvoiceStatus;
import com.invoice.platform.entity.Organization;

@Service
public class InvoiceUploadService {

    private final FileStorageService fileStorageService;
    private final PdfExtractionService pdfExtractionService;
    private final InvoiceRepository invoiceRepository;
    private final ExtractionRunRepository extractionRunRepository;
    private final InvoiceAiService invoiceAiService;

    // self-injection via proxy to ensure @Async is honoured on extractAsync()
    @Autowired @Lazy
    private InvoiceUploadService self;

    public InvoiceUploadService(FileStorageService fileStorageService,
                                PdfExtractionService pdfExtractionService,
                                InvoiceRepository invoiceRepository,
                                ExtractionRunRepository extractionRunRepository,
                                InvoiceAiService invoiceAiService) {
        this.fileStorageService = fileStorageService;
        this.pdfExtractionService = pdfExtractionService;
        this.invoiceRepository = invoiceRepository;
        this.extractionRunRepository = extractionRunRepository;
        this.invoiceAiService = invoiceAiService;
    }

    @Transactional
    public InvoiceResponse upload(MultipartFile file, Organization org) throws Exception {
        String storageKey = fileStorageService.store(file);

        Invoice invoice = new Invoice();
        invoice.setOriginalFilename(file.getOriginalFilename());
        invoice.setStorageKey(storageKey);
        invoice.setStatus(InvoiceStatus.EXTRACTING);
        invoice.setOrganization(org);
        invoice = invoiceRepository.save(invoice);

        // Pass id (Long) not the invoice object — JPA-managed entities must not cross thread boundaries.
        // Use self.extractAsync() not this.extractAsync() — @Async works only through Spring's proxy.
        // Calling this.extractAsync() bypasses the proxy and runs synchronously on the HTTP thread.
        self.extractAsync(invoice.getId());

        return InvoiceResponse.from(invoice);
    }

    // Runs on a background thread from invoiceTaskExecutor pool — not on the HTTP thread.
    // HTTP thread already returned the response to the caller before this method even starts.
    @Async("invoiceTaskExecutor")
    public void extractAsync(Long invoiceId) {

        Invoice invoice = invoiceRepository.findById(invoiceId).orElseThrow();

        // Create an audit record for this extraction attempt.
        // Tracks: which invoice, which method (PDFBox + Groq AI), start time, status.
        // Status starts as IN_PROGRESS. Saved immediately so even a mid-crash attempt is recorded.
        ExtractionRun run = ExtractionRun.start(invoice, "PDFBOX+AI");
        extractionRunRepository.save(run);

        try {
            // Resolve the full file path on disk from the storage key saved during upload.
            // e.g. storageKey "a3f9c1d2-invoice.pdf" → Path "uploads/a3f9c1d2-invoice.pdf"
            Path pdfPath = fileStorageService.resolve(invoice.getStorageKey());

            // PDFBox reads the PDF and returns all text as a plain string.
            // Works only on text-based PDFs. Scanned image PDFs return blank.
            String extractedText = pdfExtractionService.extractText(pdfPath);

            if (extractedText.isBlank()) {
                // PDF was a scanned image — no text to extract, nothing to send to AI.
                invoice.setStatus(InvoiceStatus.FAILED);
                run.fail("EMPTY_CONTENT");
            } else {
                // Send raw text to Groq AI — returns structured fields
                // (invoiceNumber, supplierName, GSTIN, date, amounts).
                // This is the expensive step — network call to Groq API.
                // Running async is exactly why this doesn't block the HTTP thread.
                InvoiceExtractionResult result = invoiceAiService.extractInvoiceFields(extractedText);

                // Copy AI-extracted fields onto the invoice.
                // Only non-null values are applied — if AI couldn't find a field, it stays null.
                applyExtraction(invoice, result);

                invoice.setStatus(InvoiceStatus.EXTRACTED);
                run.complete(); // sets status=SUCCESS and completedAt=now
            }
        } catch (Exception e) {
            // Catches anything unexpected — Groq timeout, corrupt PDF, disk read error.
            invoice.setStatus(InvoiceStatus.FAILED);
            run.fail("EXTRACTION_ERROR");
        }

        // Always runs — success or failure. Final state must always be persisted.
        // If these were inside the try block, a failure would prevent status from ever being saved.
        invoiceRepository.save(invoice);
        extractionRunRepository.save(run);
    }

    private void applyExtraction(Invoice invoice, InvoiceExtractionResult result) {
        if (result.invoiceNumber() != null)  invoice.setInvoiceNumber(result.invoiceNumber());
        if (result.supplierName() != null)   invoice.setSupplierName(result.supplierName());
        if (result.supplierGstin() != null)  invoice.setSupplierGstin(result.supplierGstin());
        if (result.invoiceDate() != null)    invoice.setInvoiceDate(result.invoiceDate());
        if (result.taxableAmount() != null)  invoice.setTaxableAmount(result.taxableAmount());
        if (result.taxAmount() != null)      invoice.setTaxAmount(result.taxAmount());
        if (result.totalAmount() != null)    invoice.setTotalAmount(result.totalAmount());
    }
}
