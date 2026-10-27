package com.invoice.platform.control.rules;

import com.invoice.platform.entity.FindingSeverity;
import com.invoice.platform.entity.Invoice;
import com.invoice.platform.entity.ValidationFinding;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class InvoiceDateRule implements ValidationRule {

    @Override
    public List<ValidationFinding> validate(Invoice invoice) {
        if (invoice.getInvoiceDate() == null) {
            return List.of();
        }

        List<ValidationFinding> findings = new ArrayList<>();
        LocalDate date = invoice.getInvoiceDate();
        LocalDate today = LocalDate.now();

        if (date.isAfter(today)) {
            findings.add(ValidationFinding.of(invoice,
                    "FUTURE_INVOICE_DATE", FindingSeverity.HIGH,
                    "Invoice date is in the future",
                    "invoiceDate", date.toString(), "On or before " + today));
        } else if (date.isBefore(today.minusYears(1))) {
            findings.add(ValidationFinding.of(invoice,
                    "INVOICE_DATE_TOO_OLD", FindingSeverity.LOW,
                    "Invoice date is more than 1 year old",
                    "invoiceDate", date.toString(), "After " + today.minusYears(1)));
        }

        return findings;
    }
}
