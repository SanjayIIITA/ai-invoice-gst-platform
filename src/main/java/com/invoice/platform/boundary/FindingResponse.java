package com.invoice.platform.boundary;

import com.invoice.platform.entity.ValidationFinding;

import java.time.Instant;

public record FindingResponse(
        Long id,
        String ruleCode,
        String severity,
        String message,
        String fieldName,
        String observedValue,
        String expectedValue,
        String status,
        Instant createdAt
) {
    public static FindingResponse from(ValidationFinding f) {
        return new FindingResponse(
                f.getId(),
                f.getRuleCode(),
                f.getSeverity().name(),
                f.getMessage(),
                f.getFieldName(),
                f.getObservedValue(),
                f.getExpectedValue(),
                f.getStatus().name(),
                f.getCreatedAt()
        );
    }
}
