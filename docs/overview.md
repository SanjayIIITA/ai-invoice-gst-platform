# AI Invoice GST Platform — Overview

---

## What This Platform Does

Upload a GST invoice PDF → AI reads it → Rules validate it → Human approves or rejects it.

```
  PDF  ──►  AI reads  ──►  Rules check  ──►  Human decides
```

That's it. Three steps. The rest is just the details of how each step works.

---

## The 5-Stage Pipeline

```
  ┌──────────┐     ┌────────────┐     ┌────────────┐     ┌─────────┐     ┌──────────┐
  │  UPLOAD  │────►│ EXTRACTION │────►│ VALIDATION │────►│ EXPLAIN │────►│  REVIEW  │
  └──────────┘     └────────────┘     └────────────┘     └─────────┘     └──────────┘
   PDF stored        AI extracts         6 rules            AI turns         Approve
   on disk           all fields          check data         findings         or Reject
                     from PDF            for errors         into English
```

---

## Invoice Status — The Journey

```
  UPLOADED
     │
     ▼
  EXTRACTING ──── AI fails ────► FAILED
     │
     ▼
  EXTRACTED
     │
     ▼
  VALIDATING
     │
     ▼
  VALIDATED
     │
     ├──────────► APPROVED
     │
     └──────────► REJECTED
```

Each status maps to one API call. No status is skipped.

---

## The Two Roles of AI

```
  ┌─────────────────────────────────────────────────────────────┐
  │  ROLE 1 — Extraction                                        │
  │                                                             │
  │  PDF text (messy)  ──►  AI  ──►  Clean JSON fields         │
  │                                                             │
  │  AI handles this because every supplier's PDF looks         │
  │  different. Rules and regex would break constantly.         │
  └─────────────────────────────────────────────────────────────┘

  ┌─────────────────────────────────────────────────────────────┐
  │  ROLE 2 — Explanation                                       │
  │                                                             │
  │  Technical findings  ──►  AI  ──►  Plain English summary   │
  │                                                             │
  │  AI handles this because a finance reviewer should not      │
  │  need to decode rule codes and severity enums.              │
  └─────────────────────────────────────────────────────────────┘
```

---

## Why Rules for Validation, Not AI

```
  ┌──────────────────────────────────┬──────────────────────────────────┐
  │  RULES  ✓                        │  AI  ✗                           │
  ├──────────────────────────────────┼──────────────────────────────────┤
  │  Same input → same result always │  May give different answers      │
  │  Fast — no API call needed       │  Slow — needs an API call        │
  │  Can explain exactly why failed  │  Cannot be audited               │
  │  Never hallucinates              │  Can hallucinate                 │
  │  10000 + 1800 = 11800, always    │  May say tax is correct when not │
  └──────────────────────────────────┴──────────────────────────────────┘

  Simple rule:  AI handles ambiguity.  Rules handle math and format.
```

---

## The 6 Validation Rules

```
  ┌──────────────────────┬──────────┬────────────────────────────────────┐
  │ Rule                 │ Severity │ What it checks                     │
  ├──────────────────────┼──────────┼────────────────────────────────────┤
  │ RequiredFields       │ HIGH     │ invoice number, supplier, date,    │
  │                      │          │ total amount — none can be null    │
  ├──────────────────────┼──────────┼────────────────────────────────────┤
  │ TaxCalculation       │ HIGH     │ taxable + tax = total (±0.01)      │
  ├──────────────────────┼──────────┼────────────────────────────────────┤
  │ GstRate              │ MEDIUM   │ rate must be 0, 5, 12, 18 or 28%  │
  ├──────────────────────┼──────────┼────────────────────────────────────┤
  │ GstinFormat          │ MEDIUM   │ must match Indian GSTIN pattern    │
  ├──────────────────────┼──────────┼────────────────────────────────────┤
  │ InvoiceDate          │ HIGH/LOW │ not in future / not older than 1yr │
  ├──────────────────────┼──────────┼────────────────────────────────────┤
  │ DuplicateInvoice     │ CRITICAL │ same number + GSTIN must not exist │
  └──────────────────────┴──────────┴────────────────────────────────────┘
```

---

## Security — How Auth Works

Every request must carry a JWT token. The token encodes who you are and which org you belong to.

```
  Register / Login
       │
       ▼
  JWT token issued
  { email, role, org_id }
       │
       ▼
  Every API call
  Authorization: Bearer <token>
       │
       ▼
  JwtAuthFilter reads token
  sets user in SecurityContext
       │
       ▼
  Controller reads @AuthenticationPrincipal
  all queries scoped to user's org_id
```

No request can touch another org's data. `org_id` always comes from the token, never from the request.

---

## Role Permissions

```
  ┌──────────┬────────────────────────────────────────────────────┐
  │ Role     │ What they can do                                   │
  ├──────────┼────────────────────────────────────────────────────┤
  │ ADMIN    │ everything — upload, extract, validate, approve,   │
  │          │ reject, view reports                               │
  ├──────────┼────────────────────────────────────────────────────┤
  │ REVIEWER │ validate, approve, reject, update findings         │
  ├──────────┼────────────────────────────────────────────────────┤
  │ VIEWER   │ read only — view invoices, findings, reports       │
  └──────────┴────────────────────────────────────────────────────┘
```

---

## API Endpoints at a Glance

```
  Auth
  POST  /api/v1/auth/register        create org + admin user
  POST  /api/v1/auth/login           get JWT token

  Invoices
  POST  /api/v1/invoices/upload      upload PDF
  POST  /api/v1/invoices/{id}/extract       trigger AI extraction
  POST  /api/v1/invoices/{id}/validate      run 6 rules
  GET   /api/v1/invoices/{id}/findings      list findings
  GET   /api/v1/invoices/{id}/explain       AI plain English summary
  PATCH /api/v1/invoices/{id}/findings/{fid} update finding status
  POST  /api/v1/invoices/{id}/approve       approve invoice
  POST  /api/v1/invoices/{id}/reject        reject with reason
  GET   /api/v1/invoices?status=VALIDATED   filter by status

  Reports
  GET   /api/v1/reports/summary      totals and counts by status
```

---

## Tech Stack

```
  ┌─────────────────────────────────────────────────────────────┐
  │  Spring Boot 4      REST API + Security + JPA               │
  │  PostgreSQL 16      persistent storage (Docker)             │
  │  Flyway             DB migrations (V1 through V7)           │
  │  PDFBox             extract raw text from PDF               │
  │  Groq LLM           AI extraction + AI explanation          │
  │  JJWT 0.12.6        JWT token generation and validation     │
  │  BCrypt             password hashing                        │
  └─────────────────────────────────────────────────────────────┘
```

---

## Architecture — BCE Layers

```
  ┌─────────────────────────────────────────────────────────────┐
  │  Boundary   Controllers, Filters, SecurityConfig,           │
  │             Request/Response DTOs                           │
  ├─────────────────────────────────────────────────────────────┤
  │  Control    Services, Repositories, Rules                   │
  ├─────────────────────────────────────────────────────────────┤
  │  Entity     JPA Entities, Enums, Exceptions                 │
  └─────────────────────────────────────────────────────────────┘

  Boundary talks to Control.
  Control talks to Entity.
  Entity never talks back up.
```
