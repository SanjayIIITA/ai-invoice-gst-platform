package com.invoice.platform.boundary;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InvoiceExtractionResult(
        String invoiceNumber,
        String supplierName,
        String supplierGstin,
        LocalDate invoiceDate,
        BigDecimal taxableAmount,
        BigDecimal taxAmount,
        BigDecimal totalAmount
) {}
