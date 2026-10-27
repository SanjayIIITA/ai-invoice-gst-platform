CREATE TABLE extraction_runs (
    id               BIGSERIAL PRIMARY KEY,
    invoice_id       BIGINT        NOT NULL REFERENCES invoices (id),
    method           VARCHAR(50)   NOT NULL,
    model_name       VARCHAR(100),
    status           VARCHAR(50)   NOT NULL,
    error_code       VARCHAR(100),
    started_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    completed_at     TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_extraction_runs_invoice_id ON extraction_runs (invoice_id);
