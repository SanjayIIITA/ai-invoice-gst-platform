package com.invoice.platform.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "validation_findings")
public class ValidationFinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(name = "rule_code", nullable = false)
    private String ruleCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FindingSeverity severity;

    @Column(nullable = false)
    private String message;

    @Column(name = "field_name")
    private String fieldName;

    @Column(name = "observed_value")
    private String observedValue;

    @Column(name = "expected_value")
    private String expectedValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FindingStatus status = FindingStatus.OPEN;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public static ValidationFinding of(Invoice invoice, String ruleCode, FindingSeverity severity,
                                       String message, String fieldName,
                                       String observedValue, String expectedValue) {
        ValidationFinding f = new ValidationFinding();
        f.invoice = invoice;
        f.ruleCode = ruleCode;
        f.severity = severity;
        f.message = message;
        f.fieldName = fieldName;
        f.observedValue = observedValue;
        f.expectedValue = expectedValue;
        return f;
    }

    public Long getId() { return id; }
    public Invoice getInvoice() { return invoice; }
    public String getRuleCode() { return ruleCode; }
    public FindingSeverity getSeverity() { return severity; }
    public String getMessage() { return message; }
    public String getFieldName() { return fieldName; }
    public String getObservedValue() { return observedValue; }
    public String getExpectedValue() { return expectedValue; }
    public FindingStatus getStatus() { return status; }
    public void setStatus(FindingStatus v) { this.status = v; }
    public Instant getCreatedAt() { return createdAt; }
}
