-- Prevents duplicate invoices at DB level even under concurrent requests.
-- Two rows with the same invoice_number + supplier_gstin within the same org
-- will cause a unique constraint violation regardless of timing.
-- NULL values are excluded — invoice_number and supplier_gstin may be null
-- before AI extraction completes.

ALTER TABLE invoices
    ADD CONSTRAINT uq_invoice_number_gstin_org
        UNIQUE (invoice_number, supplier_gstin, org_id);
