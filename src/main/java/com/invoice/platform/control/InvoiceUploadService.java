package com.invoice.platform.control;

import com.invoice.platform.boundary.InvoiceResponse;
import com.invoice.platform.entity.*;
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

    public InvoiceUploadService(FileStorageService fileStorageService,
                                PdfExtractionService pdfExtractionService,
                                InvoiceRepository invoiceRepository,
                                ExtractionRunRepository extractionRunRepository) {
        this.fileStorageService = fileStorageService;
        this.pdfExtractionService = pdfExtractionService;
        this.invoiceRepository = invoiceRepository;
        this.extractionRunRepository = extractionRunRepository;
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

        ExtractionRun run = ExtractionRun.start(invoice, "PDFBOX");
        extractionRunRepository.save(run);

        try {
            Path pdfPath = fileStorageService.resolve(storageKey);
            String extractedText = pdfExtractionService.extractText(pdfPath);

            if (extractedText.isBlank()) {
                invoice.setStatus(InvoiceStatus.FAILED);
                run.fail("EMPTY_CONTENT");
            } else {
                invoice.setStatus(InvoiceStatus.EXTRACTED);
                run.complete();
            }
        } catch (Exception e) {
            invoice.setStatus(InvoiceStatus.FAILED);
            run.fail("EXTRACTION_ERROR");
        }

        invoiceRepository.save(invoice);
        extractionRunRepository.save(run);

        return InvoiceResponse.from(invoice);
    }
}
