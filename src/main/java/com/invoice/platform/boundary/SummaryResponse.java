package com.invoice.platform.boundary;

import java.math.BigDecimal;
import java.util.Map;

public record SummaryResponse(
        long totalInvoices,
        Map<String, Long> countByStatus,
        BigDecimal totalTaxableAmount,
        BigDecimal totalTaxAmount,
        BigDecimal totalInvoiceValue
) {}
