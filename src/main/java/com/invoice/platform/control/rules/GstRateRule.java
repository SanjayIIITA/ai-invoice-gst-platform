package com.invoice.platform.control.rules;

import com.invoice.platform.entity.FindingSeverity;
import com.invoice.platform.entity.Invoice;
import com.invoice.platform.entity.ValidationFinding;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;

@Component
public class GstRateRule implements ValidationRule {

    private static final Set<BigDecimal> VALID_RATES = Set.of(
            BigDecimal.ZERO,
            new BigDecimal("5"),
            new BigDecimal("12"),
            new BigDecimal("18"),
            new BigDecimal("28")
    );

    @Override
    public List<ValidationFinding> validate(Invoice invoice) {
        if (invoice.getTaxableAmount() == null
                || invoice.getTaxAmount() == null
                || invoice.getTaxableAmount().compareTo(BigDecimal.ZERO) == 0) {
            return List.of();
        }

        BigDecimal effectiveRate = invoice.getTaxAmount()
                .divide(invoice.getTaxableAmount(), 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);

        boolean isValid = VALID_RATES.stream()
                .anyMatch(rate -> effectiveRate.compareTo(rate.setScale(2, RoundingMode.HALF_UP)) == 0);

        if (!isValid) {
            return List.of(ValidationFinding.of(invoice,
                    "INVALID_GST_RATE", FindingSeverity.MEDIUM,
                    "Effective GST rate " + effectiveRate + "% is not a standard rate (0, 5, 12, 18, 28)",
                    "taxAmount",
                    effectiveRate + "%",
                    "0%, 5%, 12%, 18% or 28%"));
        }

        return List.of();
    }
}
