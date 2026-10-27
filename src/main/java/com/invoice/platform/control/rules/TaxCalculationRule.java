package com.invoice.platform.control.rules;

import com.invoice.platform.entity.FindingSeverity;
import com.invoice.platform.entity.Invoice;
import com.invoice.platform.entity.ValidationFinding;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class TaxCalculationRule implements ValidationRule {

    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");

    @Override
    public List<ValidationFinding> validate(Invoice invoice) {
        if (invoice.getTaxableAmount() == null
                || invoice.getTaxAmount() == null
                || invoice.getTotalAmount() == null) {
            return List.of();
        }

        BigDecimal expected = invoice.getTaxableAmount().add(invoice.getTaxAmount());
        BigDecimal difference = expected.subtract(invoice.getTotalAmount()).abs();

        if (difference.compareTo(TOLERANCE) > 0) {
            return List.of(ValidationFinding.of(invoice,
                    "TAX_CALCULATION_MISMATCH", FindingSeverity.HIGH,
                    "taxableAmount + taxAmount does not equal totalAmount",
                    "totalAmount",
                    invoice.getTotalAmount().toPlainString(),
                    expected.toPlainString()));
        }

        return List.of();
    }
}
