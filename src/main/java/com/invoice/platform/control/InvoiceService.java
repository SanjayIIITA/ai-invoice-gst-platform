package com.invoice.platform.control;

import com.invoice.platform.boundary.CreateInvoiceRequest;
import com.invoice.platform.boundary.InvoiceResponse;
import com.invoice.platform.entity.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class InvoiceService {

    private final InvoiceRepository repository;

    public InvoiceService(InvoiceRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public InvoiceResponse create(CreateInvoiceRequest request, Organization org) {
        if (request.invoiceNumber() != null && repository.existsByInvoiceNumberAndOrganizationId(request.invoiceNumber(), org.getId())) {
            throw new DuplicateInvoiceException("Invoice number already exists: " + request.invoiceNumber());
        }

        Invoice invoice = new Invoice();
        invoice.setOriginalFilename(request.originalFilename());
        invoice.setStorageKey(request.storageKey());
        invoice.setSupplierName(request.supplierName());
        invoice.setSupplierGstin(request.supplierGstin());
        invoice.setInvoiceNumber(request.invoiceNumber());
        invoice.setInvoiceDate(request.invoiceDate());
        invoice.setTaxableAmount(request.taxableAmount());
        invoice.setTaxAmount(request.taxAmount());
        invoice.setTotalAmount(request.totalAmount());
        invoice.setOrganization(org);

        return InvoiceResponse.from(repository.save(invoice));
    }

    public List<InvoiceResponse> findAll(Long orgId) {
        return repository.findByOrganizationId(orgId).stream().map(InvoiceResponse::from).toList();
    }

    public List<InvoiceResponse> findByStatus(Long orgId, InvoiceStatus status) {
        return repository.findByOrganizationIdAndStatus(orgId, status).stream().map(InvoiceResponse::from).toList();
    }

    public InvoiceResponse findById(Long id, Long orgId) {
        return repository.findByIdAndOrganizationId(id, orgId)
                .map(InvoiceResponse::from)
                .orElseThrow(() -> new InvoiceNotFoundException(id));
    }

    @Transactional
    public InvoiceResponse approve(Long id, Long orgId) {
        Invoice invoice = repository.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> new InvoiceNotFoundException(id));
        invoice.setStatus(InvoiceStatus.APPROVED);
        invoice.setRejectionReason(null);
        return InvoiceResponse.from(repository.save(invoice));
    }

    @Transactional
    public InvoiceResponse reject(Long id, Long orgId, String reason) {
        Invoice invoice = repository.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> new InvoiceNotFoundException(id));
        invoice.setStatus(InvoiceStatus.REJECTED);
        invoice.setRejectionReason(reason);
        return InvoiceResponse.from(repository.save(invoice));
    }
}
