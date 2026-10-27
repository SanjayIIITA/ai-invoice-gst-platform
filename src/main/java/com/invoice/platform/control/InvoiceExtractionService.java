package com.invoice.platform.control;

import com.invoice.platform.boundary.InvoiceExtractionResult;
import com.invoice.platform.boundary.InvoiceResponse;
import com.invoice.platform.entity.Invoice;
import com.invoice.platform.entity.InvoiceNotFoundException;
import com.invoice.platform.entity.InvoiceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceExtractionService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceExtractionService.class);

    private final InvoiceRepository invoiceRepository;
    private final FileStorageService fileStorageService;
    private final PdfExtractionService pdfExtractionService;
    private final InvoiceAiService aiService;

    public InvoiceExtractionService(InvoiceRepository invoiceRepository,
                                    FileStorageService fileStorageService,
                                    PdfExtractionService pdfExtractionService,
                                    InvoiceAiService aiService) {
        this.invoiceRepository = invoiceRepository;
        this.fileStorageService = fileStorageService;
        this.pdfExtractionService = pdfExtractionService;
        this.aiService = aiService;
    }

    @Transactional
    public InvoiceResponse extract(Long invoiceId, Long orgId) {
        Invoice invoice = invoiceRepository.findByIdAndOrganizationId(invoiceId, orgId)
                .orElseThrow(() -> new InvoiceNotFoundException(invoiceId));

        try {
            String rawText = pdfExtractionService.extractText(
                    fileStorageService.resolve(invoice.getStorageKey()));

            log.info("Extracted text length: {}", rawText.length());
            log.info("Extracted text: {}", rawText);

            if (rawText.isBlank()) {
                log.warn("Empty text extracted for invoice {}", invoiceId);
                invoice.setStatus(InvoiceStatus.FAILED);
                return InvoiceResponse.from(invoiceRepository.save(invoice));
            }

            log.info("Calling AI for invoice {}", invoiceId);
            InvoiceExtractionResult result = aiService.extractInvoiceFields(rawText);
            log.info("AI result: {}", result);

            applyExtraction(invoice, result);
            invoice.setStatus(InvoiceStatus.EXTRACTED);

        } catch (Exception e) {
            log.error("Extraction failed for invoice {}: {}", invoiceId, e.getMessage(), e);
            invoice.setStatus(InvoiceStatus.FAILED);
        }

        return InvoiceResponse.from(invoiceRepository.save(invoice));
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
