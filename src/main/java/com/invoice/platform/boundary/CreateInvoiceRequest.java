package com.invoice.platform.boundary;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateInvoiceRequest(
        @NotBlank String originalFilename,
        @NotBlank String storageKey,
        String supplierName,
        String supplierGstin,
        String invoiceNumber,
        LocalDate invoiceDate,
        BigDecimal taxableAmount,
        BigDecimal taxAmount,
        BigDecimal totalAmount
) {}
