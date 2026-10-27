# Invoice Lifecycle — Complete Flow Document

---

## 1. The Big Picture

An invoice goes through 5 stages from upload to final decision.

```
┌──────────┐    ┌───────────┐    ┌────────────┐    ┌──────────────┐    ┌──────────┐
│          │    │           │    │            │    │              │    │          │
│  UPLOAD  │───►│ EXTRACTION│───►│ VALIDATION │───►│    REVIEW    │───►│ DECISION │
│          │    │           │    │            │    │              │    │          │
└──────────┘    └───────────┘    └────────────┘    └──────────────┘    └──────────┘
   PDF file       AI reads         6 rules          Human reviews        APPROVED
   stored         the PDF          check data        findings + AI        or
   in disk        extracts         for errors        explanation          REJECTED
                  fields
```

---

## 2. Invoice Status Journey

```
                    ┌─────────────────────────────────────────────────────┐
                    │                                                     │
   POST /upload     │                                                     │
        │           │                                                     │
        ▼           │                                                     │
   ┌──────────┐     │                                                     │
   │ UPLOADED │     │                                                     │
   └──────────┘     │                                                     │
        │           │                                                     │
   POST /extract    │                                                     │
        │           │                                                     │
        ▼           │                                                     │
   ┌───────────┐    │                                                     │
   │EXTRACTING │    │                                                     │
   └───────────┘    │                                                     │
        │           │                                                     │
        ├── AI success ──►  ┌───────────┐                                │
        │                   │ EXTRACTED │                                 │
        │                   └───────────┘                                 │
        │                        │                                        │
        └── AI fails ──────►  ┌────────┐                                 │
                               │ FAILED │                                 │
                               └────────┘                                 │
                                                                          │
   POST /validate                                                         │
        │                                                                 │
        ▼                                                                 │
   ┌───────────┐                                                          │
   │VALIDATING │                                                          │
   └───────────┘                                                          │
        │                                                                 │
        ▼                                                                 │
   ┌───────────┐                                                          │
   │ VALIDATED │                                                          │
   └───────────┘                                                          │
        │                                                                 │
        ├── POST /approve ──►  ┌──────────┐                              │
        │                      │ APPROVED │                              │
        │                      └──────────┘                              │
        │                                                                 │
        └── POST /reject  ──►  ┌──────────┐                              │
                               │ REJECTED │                              │
                               └──────────┘                              │
                                                                          │
                    └─────────────────────────────────────────────────────┘
```

---

## 3. Stage 1 — Upload

```
  User                  InvoiceController         InvoiceUploadService        Disk
   │                          │                          │                     │
   │  POST /upload            │                          │                     │
   │  file: invoice.pdf       │                          │                     │
   │ ────────────────────────►│                          │                     │
   │                          │  is file empty?          │                     │
   │                          │  is file a PDF?          │                     │
   │                          │  ─────────────────────── │                     │
   │                          │  uploadService.upload()  │                     │
   │                          │ ────────────────────────►│                     │
   │                          │                          │  generate UUID key  │
   │                          │                          │  store PDF on disk  │
   │                          │                          │ ───────────────────►│
   │                          │                          │  saved ◄────────────│
   │                          │                          │                     │
   │                          │                          │  save Invoice to DB │
   │                          │                          │  status = UPLOADED  │
   │                          │                          │  org_id = user's org│
   │                          │                          │                     │
   │  { id, status:UPLOADED } │◄─────────────────────────│                     │
   │ ◄────────────────────────│                          │                     │
   │                          │                          │                     │

  What is saved in DB at this point:
  ┌────┬──────────────────┬──────────────────────────────────┬──────────┐
  │ id │ original_filename│ storage_key                      │ status   │
  ├────┼──────────────────┼──────────────────────────────────┼──────────┤
  │  1 │ invoice.pdf      │ bb6712b5-68d2-43ea-92f3-....pdf  │ UPLOADED │
  └────┴──────────────────┴──────────────────────────────────┴──────────┘
  All invoice fields (supplier, amount, date) are NULL at this point
```

---

## 4. Stage 2 — AI Extraction

This is where AI plays its first and most important role.

```
  User              InvoiceController    InvoiceExtractionService    PdfExtractionService
   │                      │                       │                         │
   │  POST /{id}/extract  │                       │                         │
   │ ─────────────────────►│                       │                         │
   │                      │  extractionService    │                         │
   │                      │  .extract(id, orgId)  │                         │
   │                      │ ──────────────────────►│                         │
   │                      │                       │  load PDF from disk     │
   │                      │                       │ ────────────────────────►
   │                      │                       │  raw text ◄─────────────│
   │                      │                       │                         │
   │                      │                       │  status = EXTRACTING    │
   │                      │                       │                         │
   │                      │                       │
   │                      │                       │         InvoiceAiService (LLM)
   │                      │                       │ ───────────────────────────────►
   │                      │                       │  send raw text + prompt         │
   │                      │                       │                                 │
   │                      │                       │  { invoiceNumber,               │
   │                      │                       │    supplierName,                │
   │                      │                       │    supplierGstin,               │
   │                      │                       │    invoiceDate,                 │
   │                      │                       │    taxableAmount,               │
   │                      │                       │    taxAmount,          LLM      │
   │                      │                       │    totalAmount }   processes    │
   │                      │                       │ ◄───────────────────────────────│
   │                      │                       │                         │
   │                      │                       │  apply fields to Invoice│
   │                      │                       │  status = EXTRACTED     │
   │                      │                       │  save to DB             │
   │                      │                       │                         │
   │  { all fields filled }│◄──────────────────────│                         │
   │ ◄─────────────────────│                       │                         │
```

---

## 5. Stage 3 — Validation

```
  User              InvoiceController      ValidationEngine         6 Rules
   │                      │                      │                     │
   │  POST /{id}/validate │                      │                     │
   │ ─────────────────────►│                      │                     │
   │                      │  validationService   │                     │
   │                      │  .validate(id,orgId) │                     │
   │                      │ ─────────────────────►│                     │
   │                      │                      │  status = VALIDATING│
   │                      │                      │                     │
   │                      │                      │  run all 6 rules    │
   │                      │                      │ ───────────────────►│
   │                      │                      │                     │  RequiredFieldsRule
   │                      │                      │                     │  TaxCalculationRule
   │                      │                      │                     │  GstRateRule
   │                      │                      │                     │  GstinFormatRule
   │                      │                      │                     │  InvoiceDateRule
   │                      │                      │                     │  DuplicateInvoiceRule
   │                      │                      │                     │
   │                      │                      │  findings ◄─────────│
   │                      │                      │  save findings to DB│
   │                      │                      │  status = VALIDATED │
   │                      │                      │                     │
   │  [ list of findings ]│◄─────────────────────│                     │
   │ ◄─────────────────────│                      │                     │
```

### The 6 Validation Rules

```
  ┌─────────────────────┬──────────┬────────────────────────────────────────────┐
  │ Rule                │ Severity │ What it checks                             │
  ├─────────────────────┼──────────┼────────────────────────────────────────────┤
  │ RequiredFields      │ HIGH     │ invoiceNumber, supplierName,               │
  │                     │          │ invoiceDate, totalAmount not null          │
  ├─────────────────────┼──────────┼────────────────────────────────────────────┤
  │ TaxCalculation      │ HIGH     │ taxableAmount + taxAmount = totalAmount    │
  │                     │          │ (tolerance ±0.01)                          │
  ├─────────────────────┼──────────┼────────────────────────────────────────────┤
  │ GstRate             │ MEDIUM   │ effective rate must be 0, 5, 12, 18 or 28%│
  ├─────────────────────┼──────────┼────────────────────────────────────────────┤
  │ GstinFormat         │ MEDIUM   │ GSTIN must match Indian format             │
  │                     │          │ e.g. 29ABCDE1234F1Z5                       │
  ├─────────────────────┼──────────┼────────────────────────────────────────────┤
  │ InvoiceDate         │ HIGH/LOW │ not in future (HIGH)                       │
  │                     │          │ not older than 1 year (LOW)                │
  ├─────────────────────┼──────────┼────────────────────────────────────────────┤
  │ DuplicateInvoice    │ CRITICAL │ same invoiceNumber + supplierGstin         │
  │                     │          │ must not already exist in DB               │
  └─────────────────────┴──────────┴────────────────────────────────────────────┘

  Finding Severity Levels:
  CRITICAL ──► must be resolved before approval
  HIGH     ──► serious issue, needs attention
  MEDIUM   ──► moderate issue
  LOW      ──► informational warning
```

---

## 6. Stage 4 — AI Explanation

This is where AI plays its second role.

```
  User              InvoiceController      ValidationService       InvoiceAiService (LLM)
   │                      │                      │                         │
   │  GET /{id}/explain   │                      │                         │
   │ ─────────────────────►│                      │                         │
   │                      │  validationService   │                         │
   │                      │  .explain(id, orgId) │                         │
   │                      │ ─────────────────────►│                         │
   │                      │                      │  load findings from DB  │
   │                      │                      │                         │
   │                      │                      │  send findings to LLM   │
   │                      │                      │ ────────────────────────►
   │                      │                      │                         │  reads findings
   │                      │                      │                         │  generates plain
   │                      │                      │                         │  English explanation
   │                      │                      │  explanation ◄──────────│
   │                      │                      │                         │
   │  { explanation }     │◄─────────────────────│                         │
   │ ◄─────────────────────│                      │                         │
```

---

## 7. Stage 5 — Review and Decision

```
  Reviewer          InvoiceController         InvoiceService
   │                      │                        │
   │                      │                        │
   │  (optional)          │                        │
   │  PATCH /{id}/        │                        │
   │  findings/{fid}      │                        │
   │  { status: IGNORED } │                        │
   │ ─────────────────────►│                        │
   │                      │  update finding status │
   │                      │ ───────────────────────►│
   │  finding updated     │◄───────────────────────│
   │ ◄─────────────────────│                        │
   │                      │                        │
   │                      │                        │
   │  POST /{id}/approve  │                        │
   │ ─────────────────────►│                        │
   │                      │  status = APPROVED     │
   │                      │ ───────────────────────►│
   │  { status: APPROVED }│◄───────────────────────│
   │ ◄─────────────────────│                        │
   │                      │                        │
   │         OR           │                        │
   │                      │                        │
   │  POST /{id}/reject   │                        │
   │  { reason: "..." }   │                        │
   │ ─────────────────────►│                        │
   │                      │  status = REJECTED     │
   │                      │  rejection_reason saved│
   │                      │ ───────────────────────►│
   │  { status: REJECTED }│◄───────────────────────│
   │ ◄─────────────────────│                        │
```

---

## 8. Where AI and LLM Fit In

```
  ┌─────────────────────────────────────────────────────────────────────────┐
  │                        Invoice Pipeline                                 │
  │                                                                         │
  │   UPLOAD ──► EXTRACTION ──────────────────► VALIDATION ──► REVIEW      │
  │                   │                              │             │        │
  │                   │                              │             │        │
  │              ┌────▼────┐                    ┌────▼────┐   ┌───▼────┐   │
  │              │   AI    │                    │  RULES  │   │   AI   │   │
  │              │ ROLE 1  │                    │ (Math)  │   │ ROLE 2 │   │
  │              └─────────┘                    └─────────┘   └────────┘   │
  │              Extraction                     Validation    Explanation  │
  └─────────────────────────────────────────────────────────────────────────┘
```

### AI Role 1 — Extraction (The Hard Problem)

```
  WITHOUT AI                          WITH AI
  ──────────────────────────────────────────────────────────────
  PDF is unstructured text            AI reads the raw text
  Every supplier has different        understands context
  layout and format                   finds the right values
                                      regardless of layout

  You would need:                     AI handles:
  - regex for every format            - "Invoice No:" or "Inv#" or "Bill No"
  - positional parsing                - "₹10,000" or "10000.00" or "Rs 10000"
  - breaks on every new PDF           - dates in any format
  - maintenance nightmare             - any PDF layout
```

```
  Raw PDF Text (unstructured)
  ─────────────────────────────────────────────────────────────
  "ABC Technologies Pvt Ltd
   GSTIN: 29ABCDE1234F1Z5
   Invoice No: INV-2024-001   Date: 15-Oct-2024
   Taxable Amount: Rs 10,000
   GST @18%: Rs 1,800
   Total: Rs 11,800"
  ─────────────────────────────────────────────────────────────
                          │
                          │  AI reads this and understands it
                          ▼
  Structured JSON (clean data)
  ─────────────────────────────────────────────────────────────
  {
    "invoiceNumber":  "INV-2024-001",
    "supplierName":   "ABC Technologies Pvt Ltd",
    "supplierGstin":  "29ABCDE1234F1Z5",
    "invoiceDate":    "2024-10-15",
    "taxableAmount":  10000,
    "taxAmount":      1800,
    "totalAmount":    11800
  }
  ─────────────────────────────────────────────────────────────
```

### AI Role 2 — Explanation (Making Findings Human-Readable)

```
  WITHOUT AI                          WITH AI
  ──────────────────────────────────────────────────────────────
  Finance person sees:                Finance person sees:

  DUPLICATE_INVOICE: CRITICAL         "This invoice has the same number
  INVOICE_DATE_TOO_OLD: LOW           as one already in the system from
                                      the same supplier. Check if it was
                                      submitted twice. The invoice date
                                      is also over a year old — verify
                                      if it is still valid for payment."
```

```
  Validation Findings (technical)
  ─────────────────────────────────────────────────────────────
  - [CRITICAL] DUPLICATE_INVOICE: same number and supplier GSTIN exists
  - [LOW] INVOICE_DATE_TOO_OLD: date is more than 1 year old
  ─────────────────────────────────────────────────────────────
                          │
                          │  AI reads findings and explains them
                          ▼
  Plain English Explanation
  ─────────────────────────────────────────────────────────────
  "There are 2 issues with this invoice:

   1. Duplicate Invoice (Critical)
      An invoice with the same number already exists from this
      supplier. This could be a double submission. Verify with
      the supplier before approving.

   2. Old Invoice Date (Low)
      The invoice is dated more than a year ago. Confirm it is
      still valid for your accounting period."
  ─────────────────────────────────────────────────────────────
```

---

## 9. Why Rules for Validation, Not AI

```
  ┌──────────────────────────────────────────────────────────────────┐
  │                                                                  │
  │   RULES are used for validation because:                         │
  │                                                                  │
  │   ✓  Deterministic  — same input always gives same result        │
  │   ✓  Auditable      — you can explain exactly why it failed      │
  │   ✓  Fast           — no API call, no latency                    │
  │   ✓  Reliable       — never hallucinates                         │
  │   ✓  Precise        — 10000 + 1800 = 11800, always              │
  │                                                                  │
  │   AI is NOT used for validation because:                         │
  │                                                                  │
  │   ✗  Non-deterministic — may give different answers each time    │
  │   ✗  Can hallucinate   — may say tax is correct when it is not   │
  │   ✗  Slow              — every check needs an API call           │
  │   ✗  Not auditable     — cannot explain the exact reason         │
  │                                                                  │
  └──────────────────────────────────────────────────────────────────┘

  The right split:
  ┌─────────────────────────────────────────────────────────────┐
  │  AI handles AMBIGUITY  →  reading unstructured PDF text     │
  │  AI handles LANGUAGE   →  explaining findings in English    │
  │  RULES handle MATH     →  tax calculations, date checks     │
  │  RULES handle FORMAT   →  GSTIN pattern, required fields    │
  └─────────────────────────────────────────────────────────────┘
```

---

## 10. Complete End-to-End Flow

```
  ┌────────────────────────────────────────────────────────────────────────────────┐
  │                                                                                │
  │   1. User uploads PDF                                                          │
  │      POST /upload                                                              │
  │      └──► PDF stored on disk with UUID key                                    │
  │      └──► Invoice row created in DB  (status: UPLOADED, all fields NULL)      │
  │                                                                                │
  │   2. User triggers extraction                                                  │
  │      POST /{id}/extract                                                        │
  │      └──► PDFBox reads the PDF → raw text                                     │
  │      └──► [AI] LLM reads raw text → structured JSON fields                   │
  │      └──► Fields saved to Invoice row in DB  (status: EXTRACTED)              │
  │                                                                                │
  │   3. User triggers validation                                                  │
  │      POST /{id}/validate                                                       │
  │      └──► 6 rules run against the invoice fields                              │
  │      └──► Findings saved to validation_findings table  (status: VALIDATED)    │
  │                                                                                │
  │   4. User reads findings                                                       │
  │      GET /{id}/findings                                                        │
  │      └──► Returns list of findings with severity and message                  │
  │                                                                                │
  │   5. User asks for explanation                                                 │
  │      GET /{id}/explain                                                         │
  │      └──► [AI] LLM reads findings → plain English explanation                │
  │                                                                                │
  │   6. Reviewer updates findings (optional)                                      │
  │      PATCH /{id}/findings/{fid}                                               │
  │      └──► Mark finding as RESOLVED or IGNORED                                 │
  │                                                                                │
  │   7. Reviewer makes final decision                                             │
  │      POST /{id}/approve  →  status: APPROVED                                  │
  │      POST /{id}/reject   →  status: REJECTED  + rejection reason saved        │
  │                                                                                │
  └────────────────────────────────────────────────────────────────────────────────┘
```

---

## 11. Database Tables Involved

```
  ┌──────────────────────────────────────────────────────────────────────┐
  │  invoices                                                            │
  │  ┌────┬──────────────┬───────────────┬──────────────┬────────────┐  │
  │  │ id │ invoice_no   │ supplier_name │ total_amount │ status     │  │
  │  ├────┼──────────────┼───────────────┼──────────────┼────────────┤  │
  │  │  1 │ INV-2024-001 │ ABC Tech      │ 11800        │ VALIDATED  │  │
  │  └────┴──────────────┴───────────────┴──────────────┴────────────┘  │
  │                │                                                     │
  │                │ one invoice has many findings                       │
  │                ▼                                                     │
  │  validation_findings                                                 │
  │  ┌────┬────────────┬──────────────────────┬──────────┬────────────┐ │
  │  │ id │ invoice_id │ rule_code            │ severity │ status     │ │
  │  ├────┼────────────┼──────────────────────┼──────────┼────────────┤ │
  │  │  1 │     1      │ DUPLICATE_INVOICE    │ CRITICAL │ OPEN       │ │
  │  │  2 │     1      │ INVOICE_DATE_TOO_OLD │ LOW      │ OPEN       │ │
  │  └────┴────────────┴──────────────────────┴──────────┴────────────┘ │
  │                                                                      │
  │  extraction_runs                                                     │
  │  ┌────┬────────────┬──────────┬──────────────────────────────────┐  │
  │  │ id │ invoice_id │ tool     │ status                           │  │
  │  ├────┼────────────┼──────────┼──────────────────────────────────┤  │
  │  │  1 │     1      │ PDFBOX   │ COMPLETED                        │  │
  │  └────┴────────────┴──────────┴──────────────────────────────────┘  │
  └──────────────────────────────────────────────────────────────────────┘
```
