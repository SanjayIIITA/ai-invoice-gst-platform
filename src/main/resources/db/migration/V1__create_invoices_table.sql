CREATE TABLE invoices (
    id                BIGSERIAL PRIMARY KEY,
    original_filename VARCHAR(255)   NOT NULL,
    storage_key       VARCHAR(255)   NOT NULL UNIQUE,
    supplier_name     VARCHAR(255),
    supplier_gstin    VARCHAR(15),
    invoice_number    VARCHAR(100),
    invoice_date      DATE,
    taxable_amount    NUMERIC(19, 2),
    tax_amount        NUMERIC(19, 2),
    total_amount      NUMERIC(19, 2),
    status            VARCHAR(50)    NOT NULL DEFAULT 'UPLOADED',
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_invoices_status       ON invoices (status);
CREATE INDEX idx_invoices_invoice_number ON invoices (invoice_number);
CREATE INDEX idx_invoices_supplier_gstin ON invoices (supplier_gstin);
