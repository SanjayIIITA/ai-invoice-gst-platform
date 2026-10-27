# AI Invoice GST Platform — Interview Document

---

## What I Built

An AI-powered GST invoice processing platform. Companies upload invoice PDFs — the platform uses an LLM to extract invoice fields, runs 6 validation rules to check for errors, and lets a reviewer approve or reject the invoice. Everything is multi-tenant — each company's data is completely isolated.

**Tech Stack:** Spring Boot 4 · PostgreSQL · Groq LLM · PDFBox · JWT · BCrypt · Flyway · Docker

---

## The Pipeline

```
  PDF  →  AI reads  →  Rules validate  →  Human decides
```

| Stage | What happens |
|---|---|
| UPLOAD | PDF saved to disk, invoice row created in DB |
| EXTRACTION | PDFBox reads PDF text → AI extracts fields → saved to DB |
| VALIDATION | 6 rules run against extracted data → findings saved |
| EXPLAIN | AI turns technical findings into plain English |
| REVIEW | Reviewer approves or rejects with reason |

Each stage is a separate API call. Each stage fails and retries independently.

---

## Invoice Status Lifecycle

```
UPLOADED → EXTRACTING → EXTRACTED → VALIDATING → VALIDATED → APPROVED
                ↓                                               ↓
              FAILED                                         REJECTED
```

---

## Architecture — BCE Pattern

| Layer | Contains |
|---|---|
| Boundary | Controllers, JwtAuthFilter, SecurityConfig, DTOs, GlobalExceptionHandler |
| Control | Services, Repositories, ValidationEngine, 6 Rule classes |
| Entity | JPA Entities, Enums, Exceptions |

Flow: `Request → Boundary → Control → Entity → DB`

Boundary handles HTTP only. Control handles business logic only. Entity holds data only.

---

## The Two Roles of AI

**Role 1 — Extraction**
PDFBox gives raw unstructured text. Every supplier's PDF looks different — different labels, different date formats, different layouts. Regex breaks on every new supplier. AI reads the text semantically and returns clean JSON fields regardless of layout.

**Role 2 — Explanation**
After validation, a finance reviewer sees technical findings with rule codes and severity enums. AI translates these into plain English so the reviewer understands what is wrong without needing to know the system internals.

**AI is NOT used for validation** — validation is math and format checks. Those must be deterministic and auditable. AI can hallucinate. Rules cannot.

---

## The 6 Validation Rules

| Rule | Severity | What it checks |
|---|---|---|
| RequiredFields | HIGH | invoiceNumber, supplierName, invoiceDate, totalAmount — none can be null |
| TaxCalculation | HIGH | taxableAmount + taxAmount = totalAmount (±0.01 tolerance) |
| GstRate | MEDIUM | effective GST rate must be 0%, 5%, 12%, 18%, or 28% |
| GstinFormat | MEDIUM | must match Indian GSTIN pattern (e.g. 29ABCDE1234F1Z5) |
| InvoiceDate | HIGH / LOW | not in future → HIGH · older than 1 year → LOW |
| DuplicateInvoice | CRITICAL | same invoiceNumber + supplierGstin must not already exist in DB |

**Design pattern:** Strategy. Every rule implements `ValidationRule` interface. The engine gets all rules injected by Spring as `List<ValidationRule>`, runs them all, saves findings. Adding a new rule = one new class. Zero changes to existing code.

---

## Security & Multi-Tenancy

**Authentication flow:**
1. Register → creates Organization + ADMIN User, hashes password with BCrypt
2. Login → verifies password, issues JWT containing `{ email, role, orgId }`
3. Every request → `JwtAuthFilter` validates token, loads User, sets `SecurityContext`
4. Controller reads `@AuthenticationPrincipal` → `user.getOrganization().getId()`

**Multi-tenancy enforcement — 3 layers:**
- `orgId` is embedded in JWT at login — cannot be tampered without breaking signature
- Every DB query is scoped: `findByIdAndOrganizationId(id, orgId)`
- `orgId` always comes from `@AuthenticationPrincipal`, never from request parameters

Even if someone guesses another org's invoice ID, the query returns 404.

**Roles:**

| Role | Permissions |
|---|---|
| ADMIN | upload, extract, validate, approve, reject, reports |
| REVIEWER | validate, approve, reject, update findings |
| VIEWER | read only |

---

## Real Challenges I Solved

**1. Unstructured PDF data**
Every supplier formats their invoice differently. Regex breaks on every new template. Solution: LLM with structured prompt + `json_mode`. Works on any layout without code changes.

**2. AI hallucination risk**
AI must never validate tax calculations — it can say "looks correct" when the math is wrong. Solution: AI is only used where ambiguity exists (reading text, explaining findings). All validation is deterministic rule code.

**3. Race condition on duplicate invoices**
Java-level duplicate check (`existsByInvoiceNumber`) is not atomic — two concurrent requests can both pass the check and both insert. Solution: Added a DB UNIQUE constraint on `(invoice_number, supplier_gstin, org_id)` via Flyway migration. The DB is the only reliable guard.

**4. Spring Boot 4 breaking changes**
`DaoAuthenticationProvider.setUserDetailsService()` no longer works — login silently fails. Fix: pass `UserDetailsService` in the constructor. Also, `@RequestParam` with enum type silently returns null for invalid values. Fix: accept `String`, call `InvoiceStatus.valueOf()` manually, let `GlobalExceptionHandler` map `IllegalArgumentException` to 400.

**5. Enum sorting bug**
`OrderBySeverityAsc` on a `STRING` column sorts alphabetically — CRITICAL, HIGH, INFO, LOW, MEDIUM — wrong order. Fix: replaced method-name query with `@Query` JPQL using a `CASE` expression to define the correct sort order.

---

## API Endpoints

```
POST  /api/v1/auth/register                    create org + admin user
POST  /api/v1/auth/login                       get JWT token

POST  /api/v1/invoices/upload                  upload PDF
POST  /api/v1/invoices/{id}/extract            trigger AI extraction
POST  /api/v1/invoices/{id}/validate           run 6 rules
GET   /api/v1/invoices/{id}/findings           list findings
GET   /api/v1/invoices/{id}/explain            AI plain English summary
PATCH /api/v1/invoices/{id}/findings/{fid}     update finding status
POST  /api/v1/invoices/{id}/approve            approve invoice
POST  /api/v1/invoices/{id}/reject             reject with reason
GET   /api/v1/invoices?status=VALIDATED        filter by status
GET   /api/v1/reports/summary                  counts and totals by status
```

---

## Production Roadmap

| Current (dev) | Production (AWS) |
|---|---|
| PDF on local disk | AWS S3 |
| Groq LLM — sync call | Async: SQS queue + worker |
| Single Spring Boot instance | ECS containers, load balanced |
| Postgres in Docker | AWS RDS Multi-AZ |
| JWT secret in .properties | AWS Secrets Manager |

---

## Quick Answers

**Why BCE not MVC?** MVC is for web apps with views. This is a pure REST API — no views. BCE maps better: Boundary = API surface, Control = logic, Entity = data.

**Why Flyway?** Every schema change is a versioned SQL file (V1–V8). Runs automatically on startup. You can always see exactly what changed and when.

**Why PDFs on disk not DB?** Binary blobs bloat the DB and slow every query. Files belong in file storage. DB stores only metadata and a UUID pointer to the file.

**Why separate API calls per stage?** Each stage fails and retries independently. Extraction failure doesn't lose the upload. Long AI calls don't block a single HTTP request indefinitely.

**What is OncePerRequestFilter?** Runs exactly once per HTTP request. Used for `JwtAuthFilter` to avoid processing the token twice.

**Why CSRF disabled?** CSRF exploits cookie-based sessions. This API uses JWT in the Authorization header — not cookies. CSRF is not a threat here.

**Why BCrypt over MD5/SHA?** BCrypt is slow by design — ~100ms per hash. Fast enough for login, too slow for bulk GPU cracking. Also generates a unique salt per password.
