package com.invoice.platform.control;

import com.invoice.platform.control.rules.ValidationRule;
import com.invoice.platform.entity.Invoice;
import com.invoice.platform.entity.InvoiceStatus;
import com.invoice.platform.entity.ValidationFinding;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ValidationEngine {

    private final List<ValidationRule> rules;
    private final ValidationFindingRepository findingRepository;
    private final InvoiceRepository invoiceRepository;

    public ValidationEngine(List<ValidationRule> rules,
                            ValidationFindingRepository findingRepository,
                            InvoiceRepository invoiceRepository) {
        this.rules = rules;
        this.findingRepository = findingRepository;
        this.invoiceRepository = invoiceRepository;
    }

    @Transactional
    public List<ValidationFinding> validate(Invoice invoice) {
        invoice.setStatus(InvoiceStatus.VALIDATING);
        invoiceRepository.save(invoice);

        List<ValidationFinding> findings = rules.stream()
                .flatMap(rule -> rule.validate(invoice).stream())
                .toList();

        findingRepository.saveAll(findings);

        invoice.setStatus(InvoiceStatus.VALIDATED);
        invoiceRepository.save(invoice);

        return findings;
    }
}
