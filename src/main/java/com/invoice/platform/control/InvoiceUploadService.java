package com.invoice.platform.control;

import com.invoice.platform.boundary.InvoiceExtractionResult;
import com.invoice.platform.boundary.InvoiceResponse;
import com.invoice.platform.entity.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

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

        self.extractAsync(invoice.getId());

        return InvoiceResponse.from(invoice);
    }

    @Async("invoiceTaskExecutor")
    public void extractAsync(Long invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId).orElseThrow();
        ExtractionRun run = ExtractionRun.start(invoice, "PDFBOX+AI");
        extractionRunRepository.save(run);

        try {
            Path pdfPath = fileStorageService.resolve(invoice.getStorageKey());
            String extractedText = pdfExtractionService.extractText(pdfPath);

            if (extractedText.isBlank()) {
                invoice.setStatus(InvoiceStatus.FAILED);
                run.fail("EMPTY_CONTENT");
            } else {
                InvoiceExtractionResult result = invoiceAiService.extractInvoiceFields(extractedText);
                applyExtraction(invoice, result);
                invoice.setStatus(InvoiceStatus.EXTRACTED);
                run.complete();
            }
        } catch (Exception e) {
            invoice.setStatus(InvoiceStatus.FAILED);
            run.fail("EXTRACTION_ERROR");
        }

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
