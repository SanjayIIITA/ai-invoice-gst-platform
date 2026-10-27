package com.invoice.platform.control.rules;

import com.invoice.platform.control.InvoiceRepository;
import com.invoice.platform.entity.FindingSeverity;
import com.invoice.platform.entity.Invoice;
import com.invoice.platform.entity.ValidationFinding;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DuplicateInvoiceRule implements ValidationRule {

    private final InvoiceRepository invoiceRepository;

    public DuplicateInvoiceRule(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    @Override
    public List<ValidationFinding> validate(Invoice invoice) {
        if (invoice.getInvoiceNumber() == null || invoice.getSupplierGstin() == null) {
            return List.of();
        }

        boolean duplicate = invoiceRepository
                .existsByInvoiceNumberAndSupplierGstinAndIdNotAndOrganizationId(
                        invoice.getInvoiceNumber(),
                        invoice.getSupplierGstin(),
                        invoice.getId(),
                        invoice.getOrganization().getId());

        if (duplicate) {
            return List.of(ValidationFinding.of(invoice,
                    "DUPLICATE_INVOICE", FindingSeverity.CRITICAL,
                    "An invoice with the same number and supplier GSTIN already exists",
                    "invoiceNumber",
                    invoice.getInvoiceNumber(),
                    "Unique invoice number per supplier"));
        }

        return List.of();
    }
}
