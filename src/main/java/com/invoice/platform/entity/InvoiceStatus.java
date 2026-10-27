package com.invoice.platform.entity;

public enum InvoiceStatus {
    UPLOADED,
    EXTRACTING,
    EXTRACTED,
    FAILED,
    VALIDATING,
    VALIDATED,
    PENDING_REVIEW,
    APPROVED,
    REJECTED
}
