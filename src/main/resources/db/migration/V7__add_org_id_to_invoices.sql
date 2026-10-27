ALTER TABLE invoices ADD COLUMN org_id BIGINT REFERENCES organizations(id);
