package com.invoice.platform.control.rules;

import com.invoice.platform.entity.Invoice;
import com.invoice.platform.entity.ValidationFinding;

import java.util.List;

public interface ValidationRule {
    List<ValidationFinding> validate(Invoice invoice);
}
