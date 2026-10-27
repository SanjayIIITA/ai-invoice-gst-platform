package com.invoice.platform.control;

import com.invoice.platform.boundary.SummaryResponse;
import com.invoice.platform.entity.Invoice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ReportService {

    private final InvoiceRepository invoiceRepository;

    public ReportService(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    public SummaryResponse summary(Long orgId) {
        List<Invoice> all = invoiceRepository.findByOrganizationId(orgId);

        Map<String, Long> countByStatus = all.stream()
                .collect(Collectors.groupingBy(i -> i.getStatus().name(), Collectors.counting()));

        BigDecimal totalTaxable = sum(all.stream().map(Invoice::getTaxableAmount).toList());
        BigDecimal totalTax     = sum(all.stream().map(Invoice::getTaxAmount).toList());
        BigDecimal totalValue   = sum(all.stream().map(Invoice::getTotalAmount).toList());

        return new SummaryResponse(all.size(), countByStatus, totalTaxable, totalTax, totalValue);
    }

    private BigDecimal sum(List<BigDecimal> values) {
        return values.stream()
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
