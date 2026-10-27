# Sample Invoice Collection — API Curl Commands

> Complete curl commands for all 5 sample invoices.
> Run them in order — upload → extract → validate → explain → approve/reject.

---

## Prerequisites

### Step 1 — Register (first time only)

```bash
curl --location 'http://localhost:8080/api/v1/auth/register' \
--header 'Content-Type: application/json' \
--data '{
  "email": "admin@testorg.com",
  "password": "password123",
  "fullName": "Admin User",
  "organizationName": "Test Org"
}'
```

### Step 2 — Login (if already registered)

```bash
curl --location 'http://localhost:8080/api/v1/auth/login' \
--header 'Content-Type: application/json' \
--data '{
  "email": "admin@testorg.com",
  "password": "password123"
}'
```

> Copy the `token` from the response and replace `<TOKEN>` in all commands below.

---

## Invoice Details

```
  ┌─────────────┬──────────────────────────┬─────────────────┬────────────┬──────────┬──────────┬──────────┬──────┐
  │ Invoice No  │ Supplier                 │ GSTIN           │ Date       │ Taxable  │ GST      │ Total    │ Rate │
  ├─────────────┼──────────────────────────┼─────────────────┼────────────┼──────────┼──────────┼──────────┼──────┤
  │ INV-2024-001│ ABC Technologies Pvt Ltd │ 29ABCDE1234F1Z5 │ 2024-10-15 │ 10,000   │ 1,800    │ 11,800   │ 18%  │
  │ INV-2024-002│ XYZ Supplies Pvt Ltd     │ 27XYZAB5678G1Z3 │ 2024-11-20 │  5,000   │   900    │  5,900   │ 18%  │
  │ INV-2024-003│ PQR Services Ltd         │ 07PQRCD9012H1Z1 │ 2024-12-05 │ 20,000   │ 2,400    │ 22,400   │ 12%  │
  │ INV-2025-001│ LMN Traders              │ 33LMNEF3456I1Z8 │ 2025-01-10 │  8,000   │   400    │  8,400   │  5%  │
  │ INV-2025-002│ DEF Consulting Pvt Ltd   │ 19DEFGH7890J1Z6 │ 2025-03-22 │ 15,000   │ 2,700    │ 17,700   │ 18%  │
  └─────────────┴──────────────────────────┴─────────────────┴────────────┴──────────┴──────────┴──────────┴──────┘
```

---

## INV-2024-001 — ABC Technologies Pvt Ltd (18% GST)

### Upload
```bash
curl --location 'http://localhost:8080/api/v1/invoices/upload' \
--header 'Authorization: Bearer <TOKEN>' \
--form 'file=@"/home/sanjay-kumar/projects/ai-invoice-gst-platform/samples/INV-2024-001.pdf"'
```

### Extract
```bash
curl --location --request POST 'http://localhost:8080/api/v1/invoices/1/extract' \
--header 'Authorization: Bearer <TOKEN>'
```

### Validate
```bash
curl --location --request POST 'http://localhost:8080/api/v1/invoices/1/validate' \
--header 'Authorization: Bearer <TOKEN>'
```

### View Findings
```bash
curl --location 'http://localhost:8080/api/v1/invoices/1/findings' \
--header 'Authorization: Bearer <TOKEN>'
```

### Explain
```bash
curl --location 'http://localhost:8080/api/v1/invoices/1/explain' \
--header 'Authorization: Bearer <TOKEN>'
```

### Approve
```bash
curl --location --request POST 'http://localhost:8080/api/v1/invoices/1/approve' \
--header 'Authorization: Bearer <TOKEN>'
```

---

## INV-2024-002 — XYZ Supplies Pvt Ltd (18% GST)

### Upload
```bash
curl --location 'http://localhost:8080/api/v1/invoices/upload' \
--header 'Authorization: Bearer <TOKEN>' \
--form 'file=@"/home/sanjay-kumar/projects/ai-invoice-gst-platform/samples/INV-2024-002.pdf"'
```

### Extract
```bash
curl --location --request POST 'http://localhost:8080/api/v1/invoices/2/extract' \
--header 'Authorization: Bearer <TOKEN>'
```

### Validate
```bash
curl --location --request POST 'http://localhost:8080/api/v1/invoices/2/validate' \
--header 'Authorization: Bearer <TOKEN>'
```

### View Findings
```bash
curl --location 'http://localhost:8080/api/v1/invoices/2/findings' \
--header 'Authorization: Bearer <TOKEN>'
```

### Explain
```bash
curl --location 'http://localhost:8080/api/v1/invoices/2/explain' \
--header 'Authorization: Bearer <TOKEN>'
```

### Approve
```bash
curl --location --request POST 'http://localhost:8080/api/v1/invoices/2/approve' \
--header 'Authorization: Bearer <TOKEN>'
```

---

## INV-2024-003 — PQR Services Ltd (12% GST)

### Upload
```bash
curl --location 'http://localhost:8080/api/v1/invoices/upload' \
--header 'Authorization: Bearer <TOKEN>' \
--form 'file=@"/home/sanjay-kumar/projects/ai-invoice-gst-platform/samples/INV-2024-003.pdf"'
```

### Extract
```bash
curl --location --request POST 'http://localhost:8080/api/v1/invoices/3/extract' \
--header 'Authorization: Bearer <TOKEN>'
```

### Validate
```bash
curl --location --request POST 'http://localhost:8080/api/v1/invoices/3/validate' \
--header 'Authorization: Bearer <TOKEN>'
```

### View Findings
```bash
curl --location 'http://localhost:8080/api/v1/invoices/3/findings' \
--header 'Authorization: Bearer <TOKEN>'
```

### Explain
```bash
curl --location 'http://localhost:8080/api/v1/invoices/3/explain' \
--header 'Authorization: Bearer <TOKEN>'
```

### Reject (example with reason)
```bash
curl --location --request POST 'http://localhost:8080/api/v1/invoices/3/reject' \
--header 'Authorization: Bearer <TOKEN>' \
--header 'Content-Type: application/json' \
--data '{"reason": "Tax calculation does not match our records"}'
```

---

## INV-2025-001 — LMN Traders (5% GST)

### Upload
```bash
curl --location 'http://localhost:8080/api/v1/invoices/upload' \
--header 'Authorization: Bearer <TOKEN>' \
--form 'file=@"/home/sanjay-kumar/projects/ai-invoice-gst-platform/samples/INV-2025-001.pdf"'
```

### Extract
```bash
curl --location --request POST 'http://localhost:8080/api/v1/invoices/4/extract' \
--header 'Authorization: Bearer <TOKEN>'
```

### Validate
```bash
curl --location --request POST 'http://localhost:8080/api/v1/invoices/4/validate' \
--header 'Authorization: Bearer <TOKEN>'
```

### View Findings
```bash
curl --location 'http://localhost:8080/api/v1/invoices/4/findings' \
--header 'Authorization: Bearer <TOKEN>'
```

### Explain
```bash
curl --location 'http://localhost:8080/api/v1/invoices/4/explain' \
--header 'Authorization: Bearer <TOKEN>'
```

### Approve
```bash
curl --location --request POST 'http://localhost:8080/api/v1/invoices/4/approve' \
--header 'Authorization: Bearer <TOKEN>'
```

---

## INV-2025-002 — DEF Consulting Pvt Ltd (18% GST)

### Upload
```bash
curl --location 'http://localhost:8080/api/v1/invoices/upload' \
--header 'Authorization: Bearer <TOKEN>' \
--form 'file=@"/home/sanjay-kumar/projects/ai-invoice-gst-platform/samples/INV-2025-002.pdf"'
```

### Extract
```bash
curl --location --request POST 'http://localhost:8080/api/v1/invoices/5/extract' \
--header 'Authorization: Bearer <TOKEN>'
```

### Validate
```bash
curl --location --request POST 'http://localhost:8080/api/v1/invoices/5/validate' \
--header 'Authorization: Bearer <TOKEN>'
```

### View Findings
```bash
curl --location 'http://localhost:8080/api/v1/invoices/5/findings' \
--header 'Authorization: Bearer <TOKEN>'
```

### Explain
```bash
curl --location 'http://localhost:8080/api/v1/invoices/5/explain' \
--header 'Authorization: Bearer <TOKEN>'
```

### Approve
```bash
curl --location --request POST 'http://localhost:8080/api/v1/invoices/5/approve' \
--header 'Authorization: Bearer <TOKEN>'
```

---

## Other Useful Commands

### List all invoices
```bash
curl --location 'http://localhost:8080/api/v1/invoices' \
--header 'Authorization: Bearer <TOKEN>'
```

### Filter by status
```bash
curl --location 'http://localhost:8080/api/v1/invoices?status=VALIDATED' \
--header 'Authorization: Bearer <TOKEN>'
```

> Valid status values: `UPLOADED`, `EXTRACTING`, `EXTRACTED`, `FAILED`, `VALIDATING`, `VALIDATED`, `APPROVED`, `REJECTED`

### Get single invoice
```bash
curl --location 'http://localhost:8080/api/v1/invoices/1' \
--header 'Authorization: Bearer <TOKEN>'
```

### Update a finding status
```bash
curl --location --request PATCH 'http://localhost:8080/api/v1/invoices/1/findings/1' \
--header 'Authorization: Bearer <TOKEN>' \
--header 'Content-Type: application/json' \
--data '{"status": "IGNORED"}'
```

> Valid finding status values: `OPEN`, `ACKNOWLEDGED`, `RESOLVED`, `IGNORED`

### Summary report
```bash
curl --location 'http://localhost:8080/api/v1/reports/summary' \
--header 'Authorization: Bearer <TOKEN>'
```

---

## Notes

- Invoice IDs (1–5) assume a fresh database. If you already have invoices, check the `id` in the upload response and use that.
- Token expires in 24 hours — login again to get a fresh one.
- Each invoice must go through upload → extract → validate before approve/reject.
