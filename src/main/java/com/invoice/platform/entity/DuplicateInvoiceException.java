package com.invoice.platform.entity;

public class DuplicateInvoiceException extends RuntimeException {
    public DuplicateInvoiceException(String message) {
        super(message);
    }
}
