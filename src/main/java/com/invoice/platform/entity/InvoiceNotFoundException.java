package com.invoice.platform.entity;

public class InvoiceNotFoundException extends RuntimeException {
    public InvoiceNotFoundException(Long id) {
        super("Invoice not found: " + id);
    }
}
