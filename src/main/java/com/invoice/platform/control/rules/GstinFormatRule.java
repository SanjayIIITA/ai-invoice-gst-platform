package com.invoice.platform.control.rules;

import com.invoice.platform.entity.FindingSeverity;
import com.invoice.platform.entity.Invoice;
import com.invoice.platform.entity.ValidationFinding;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class GstinFormatRule implements ValidationRule {

    // 2 digit state code + 10 char PAN + entity number + Z + checksum
    private static final Pattern GSTIN_PATTERN =
            Pattern.compile("^[0-3][0-9][A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$");

    @Override
    public List<ValidationFinding> validate(Invoice invoice) {
        String gstin = invoice.getSupplierGstin();

        if (gstin == null || gstin.isBlank()) {
            return List.of(ValidationFinding.of(invoice,
                    "MISSING_GSTIN", FindingSeverity.MEDIUM,
                    "Supplier GSTIN is missing", "supplierGstin", null, null));
        }

        if (!GSTIN_PATTERN.matcher(gstin.toUpperCase()).matches()) {
            return List.of(ValidationFinding.of(invoice,
                    "INVALID_GSTIN_FORMAT", FindingSeverity.MEDIUM,
                    "Supplier GSTIN format is invalid",
                    "supplierGstin", gstin, "Format: 29ABCDE1234F1Z5"));
        }

        return List.of();
    }
}
