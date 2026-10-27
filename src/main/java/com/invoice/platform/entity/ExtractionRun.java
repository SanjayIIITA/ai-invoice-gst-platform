package com.invoice.platform.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "extraction_runs")
public class ExtractionRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(nullable = false)
    private String method;

    @Column(name = "model_name")
    private String modelName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExtractionStatus status;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    public static ExtractionRun start(Invoice invoice, String method) {
        ExtractionRun run = new ExtractionRun();
        run.invoice = invoice;
        run.method = method;
        run.status = ExtractionStatus.IN_PROGRESS;
        return run;
    }

    public void complete() {
        this.status = ExtractionStatus.SUCCESS;
        this.completedAt = Instant.now();
    }

    public void fail(String errorCode) {
        this.status = ExtractionStatus.FAILED;
        this.errorCode = errorCode;
        this.completedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Invoice getInvoice() { return invoice; }
    public String getMethod() { return method; }
    public ExtractionStatus getStatus() { return status; }
    public String getErrorCode() { return errorCode; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
}
