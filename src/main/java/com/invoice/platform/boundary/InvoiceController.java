package com.invoice.platform.boundary;

import com.invoice.platform.control.InvoiceExtractionService;
import com.invoice.platform.control.InvoiceService;
import com.invoice.platform.control.InvoiceUploadService;
import com.invoice.platform.control.ValidationService;
import com.invoice.platform.entity.FindingStatus;
import com.invoice.platform.entity.InvoiceStatus;
import com.invoice.platform.entity.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/invoices")
public class InvoiceController {

    private final InvoiceService service;
    private final InvoiceUploadService uploadService;
    private final ValidationService validationService;
    private final InvoiceExtractionService extractionService;

    public InvoiceController(InvoiceService service,
                             InvoiceUploadService uploadService,
                             ValidationService validationService,
                             InvoiceExtractionService extractionService) {
        this.service = service;
        this.uploadService = uploadService;
        this.validationService = validationService;
        this.extractionService = extractionService;
    }

    @PostMapping
    public ResponseEntity<InvoiceResponse> create(@Valid @RequestBody CreateInvoiceRequest request,
                                                   @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request, user.getOrganization()));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<InvoiceResponse> upload(@RequestParam("file") MultipartFile file,
                                                   @AuthenticationPrincipal User user) throws Exception {
        if (file.isEmpty()) throw new InvalidFileException("File must not be empty");
        if (file.getContentType() == null || !file.getContentType().equals(MediaType.APPLICATION_PDF_VALUE))
            throw new InvalidFileException("Only PDF files are accepted");
        return ResponseEntity.status(HttpStatus.CREATED).body(uploadService.upload(file, user.getOrganization()));
    }

    @GetMapping
    public ResponseEntity<List<InvoiceResponse>> findAll(@RequestParam(required = false) String status,
                                                          @AuthenticationPrincipal User user) {
        Long orgId = user.getOrganization().getId();
        if (status != null) {
            return ResponseEntity.ok(service.findByStatus(orgId, InvoiceStatus.valueOf(status.toUpperCase())));
        }
        return ResponseEntity.ok(service.findAll(orgId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InvoiceResponse> findById(@PathVariable Long id,
                                                     @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(service.findById(id, user.getOrganization().getId()));
    }

    @PostMapping("/{id}/extract")
    public ResponseEntity<InvoiceResponse> extract(@PathVariable Long id,
                                                    @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(extractionService.extract(id, user.getOrganization().getId()));
    }

    @PostMapping("/{id}/validate")
    public ResponseEntity<List<FindingResponse>> validate(@PathVariable Long id,
                                                           @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(validationService.validate(id, user.getOrganization().getId()));
    }

    @GetMapping("/{id}/findings")
    public ResponseEntity<List<FindingResponse>> findings(@PathVariable Long id,
                                                           @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(validationService.findingsFor(id, user.getOrganization().getId()));
    }

    @PatchMapping("/{id}/findings/{findingId}")
    public ResponseEntity<FindingResponse> updateFinding(@PathVariable Long id,
                                                          @PathVariable Long findingId,
                                                          @Valid @RequestBody UpdateFindingRequest request,
                                                          @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(validationService.updateFinding(id, findingId, request.status(), user.getOrganization().getId()));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<InvoiceResponse> approve(@PathVariable Long id,
                                                    @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(service.approve(id, user.getOrganization().getId()));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<InvoiceResponse> reject(@PathVariable Long id,
                                                   @Valid @RequestBody RejectRequest request,
                                                   @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(service.reject(id, user.getOrganization().getId(), request.reason()));
    }

    @GetMapping("/{id}/explain")
    public ResponseEntity<ExplainResponse> explain(@PathVariable Long id,
                                                    @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(validationService.explain(id, user.getOrganization().getId()));
    }
}
