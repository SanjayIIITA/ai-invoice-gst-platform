package com.invoice.platform.boundary;

import com.invoice.platform.entity.Invoice;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record InvoiceResponse(
        Long id,
        String originalFilename,
        String storageKey,
        String supplierName,
        String supplierGstin,
        String invoiceNumber,
        LocalDate invoiceDate,
        BigDecimal taxableAmount,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        String status,
        String rejectionReason,
        Instant createdAt,
        Instant updatedAt
) {
    public static InvoiceResponse from(Invoice invoice) {
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getOriginalFilename(),
                invoice.getStorageKey(),
                invoice.getSupplierName(),
                invoice.getSupplierGstin(),
                invoice.getInvoiceNumber(),
                invoice.getInvoiceDate(),
                invoice.getTaxableAmount(),
                invoice.getTaxAmount(),
                invoice.getTotalAmount(),
                invoice.getStatus().name(),
                invoice.getRejectionReason(),
                invoice.getCreatedAt(),
                invoice.getUpdatedAt()
        );
    }
}
