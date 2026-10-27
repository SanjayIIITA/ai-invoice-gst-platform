CREATE TABLE validation_findings (
    id               BIGSERIAL PRIMARY KEY,
    invoice_id       BIGINT        NOT NULL REFERENCES invoices (id),
    rule_code        VARCHAR(100)  NOT NULL,
    severity         VARCHAR(20)   NOT NULL,
    message          TEXT          NOT NULL,
    field_name       VARCHAR(100),
    observed_value   VARCHAR(255),
    expected_value   VARCHAR(255),
    status           VARCHAR(50)   NOT NULL DEFAULT 'OPEN',
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_findings_invoice_id ON validation_findings (invoice_id);
CREATE INDEX idx_findings_severity   ON validation_findings (severity);
