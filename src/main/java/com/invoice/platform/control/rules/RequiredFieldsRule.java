package com.invoice.platform.control.rules;

import com.invoice.platform.entity.FindingSeverity;
import com.invoice.platform.entity.Invoice;
import com.invoice.platform.entity.ValidationFinding;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RequiredFieldsRule implements ValidationRule {

    @Override
    public List<ValidationFinding> validate(Invoice invoice) {
        List<ValidationFinding> findings = new ArrayList<>();

        if (isBlank(invoice.getInvoiceNumber())) {
            findings.add(ValidationFinding.of(invoice,
                    "MISSING_INVOICE_NUMBER", FindingSeverity.HIGH,
                    "Invoice number is missing", "invoiceNumber", null, null));
        }
        if (isBlank(invoice.getSupplierName())) {
            findings.add(ValidationFinding.of(invoice,
                    "MISSING_SUPPLIER_NAME", FindingSeverity.HIGH,
                    "Supplier name is missing", "supplierName", null, null));
        }
        if (invoice.getInvoiceDate() == null) {
            findings.add(ValidationFinding.of(invoice,
                    "MISSING_INVOICE_DATE", FindingSeverity.HIGH,
                    "Invoice date is missing", "invoiceDate", null, null));
        }
        if (invoice.getTotalAmount() == null) {
            findings.add(ValidationFinding.of(invoice,
                    "MISSING_TOTAL_AMOUNT", FindingSeverity.HIGH,
                    "Total amount is missing", "totalAmount", null, null));
        }

        return findings;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
