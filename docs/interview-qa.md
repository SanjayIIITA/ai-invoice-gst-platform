# Interview Q&A — AI Invoice GST Platform

> A complete guide to confidently explain this project in any interview.
> Covers introduction, architecture, AI, security, challenges, and tricky follow-ups.

---

## 🧭 Quick Reference

```
  What it does   →   Automates GST invoice processing using AI + rules
  Stack          →   Spring Boot 4, PostgreSQL, Groq LLM, JWT, PDFBox
  Architecture   →   BCE (Boundary → Control → Entity)
  AI roles       →   Extraction (PDF → JSON)  +  Explanation (findings → English)
  Security       →   JWT multi-tenant, org-scoped queries, role-based access
```

---

## Section 1 — Project Introduction

---

**Q: Tell me about this project.**

> It's an AI-powered GST invoice processing platform. Companies upload invoice PDFs — the platform uses an LLM to extract the invoice fields, runs 6 validation rules to check for errors, and lets a reviewer approve or reject the invoice. Everything is multi-tenant — each company's data is completely isolated.

---

**Q: What real problem does this solve?**

> Finance teams receive hundreds of GST invoices every month as PDFs. They manually open each one, check if the tax math is correct, verify the GSTIN format, look for duplicates — it's repetitive and error-prone. One wrong invoice that gets approved can cause GST filing issues. This platform automates the entire check process and flags problems before a human even looks at it.

---

**Q: Walk me through the full flow end to end.**

```
  ┌─────────┐    ┌───────────┐    ┌────────────┐    ┌─────────┐    ┌──────────┐
  │  UPLOAD │───►│ EXTRACTION│───►│ VALIDATION │───►│ EXPLAIN │───►│  REVIEW  │
  └─────────┘    └───────────┘    └────────────┘    └─────────┘    └──────────┘
   PDF saved       AI reads          6 rules           AI turns        Approve
   to disk         PDF → JSON        check data        findings        or Reject
                                     for errors        to English
```

> Each stage is a separate API call. Each call moves the invoice to a new status. If any stage fails, the invoice stops there and can be retried.

---

**Q: What is the invoice status lifecycle?**

```
  UPLOADED
     │
     ▼
  EXTRACTING ─── AI fails ──► FAILED  (can retry)
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
     ├──► APPROVED
     └──► REJECTED  (with rejection reason saved)
```

---

## Section 2 — Architecture

---

**Q: What architecture pattern did you follow?**

> BCE — Boundary, Control, Entity. It's a clean layered architecture designed for REST APIs.

```
  ┌──────────────────────────────────────────────────────────────┐
  │  BOUNDARY   Controllers · Filters · SecurityConfig · DTOs    │
  ├──────────────────────────────────────────────────────────────┤
  │  CONTROL    Services · Repositories · Validation Rules       │
  ├──────────────────────────────────────────────────────────────┤
  │  ENTITY     JPA Entities · Enums · Exceptions                │
  └──────────────────────────────────────────────────────────────┘

  Flow:  Request → Boundary → Control → Entity → DB
         Response ← Boundary ← Control ← Entity ← DB
```

> Boundary only handles HTTP. Control only handles business logic. Entity only holds data. Nothing crosses layers in the wrong direction.

---

**Q: Why BCE and not MVC?**

> MVC was designed for web apps with views — Model, View, Controller. This is a pure REST API. There are no views. BCE maps better: Boundary is the API surface, Control is the logic, Entity is the data. It's also easier to explain to a team — every file has an obvious home.

---

**Q: How many classes are in each layer?**

```
  Boundary (19)   AuthController, InvoiceController, ReportController,
                  JwtAuthFilter, SecurityConfig, GlobalExceptionHandler,
                  + all request/response DTOs

  Control  (16)   AuthService, InvoiceService, InvoiceUploadService,
                  InvoiceExtractionService, ValidationService, ValidationEngine,
                  InvoiceAiService, ReportService, JwtService, FileStorageService,
                  PdfExtractionService, + 5 repositories + 6 rule classes

  Entity   (12)   Invoice, User, Organization, ValidationFinding, ExtractionRun,
                  InvoiceStatus, Role, FindingSeverity, FindingStatus,
                  ExtractionStatus, + 2 exception classes
```

---

**Q: How do the services divide responsibility?**

```
  InvoiceUploadService      →  save PDF to disk, create Invoice row in DB
  InvoiceExtractionService  →  orchestrate PDF read + AI call + save fields
  PdfExtractionService      →  read PDF bytes → raw text (PDFBox)
  InvoiceAiService          →  send text to LLM, parse response
  ValidationEngine          →  run all 6 rules, save findings
  ValidationService         →  orchestrate validate + explain endpoints
  InvoiceService            →  approve, reject, list, filter by status
  ReportService             →  summary counts and totals
  AuthService               →  register, login, token generation
  JwtService                →  create token, validate token, extract claims
```

> Each service does one thing. No service does two jobs.

---

## Section 3 — AI Integration

---

**Q: Where exactly does AI fit in this platform?**

> AI has exactly two roles. Nothing else uses AI.

```
  ┌──────────────────────────────────────────────────────────────┐
  │  ROLE 1 — Extraction                                         │
  │                                                              │
  │  Unstructured PDF text  ──►  LLM  ──►  Clean JSON fields    │
  │                                                              │
  │  Why AI?  Every supplier's PDF looks different.              │
  │           Regex breaks on every new format.                  │
  │           AI understands context, not just patterns.         │
  └──────────────────────────────────────────────────────────────┘

  ┌──────────────────────────────────────────────────────────────┐
  │  ROLE 2 — Explanation                                        │
  │                                                              │
  │  Technical findings  ──►  LLM  ──►  Plain English summary   │
  │                                                              │
  │  Why AI?  A finance reviewer should not need to decode       │
  │           rule codes and severity enums.                     │
  │           AI translates technical output to human language.  │
  └──────────────────────────────────────────────────────────────┘
```

---

**Q: Why use AI for extraction instead of parsing the PDF directly?**

> PDF parsing gives you raw text — but that text is unstructured. Every supplier formats their invoice differently:

```
  Supplier A writes:   "Invoice No: INV-2024-001"
  Supplier B writes:   "Inv# : INV-2024-001"
  Supplier C writes:   "Bill Number  INV-2024-001"

  Supplier A writes:   "Date: 15-Oct-2024"
  Supplier B writes:   "Invoice Date: 2024/10/15"
  Supplier C writes:   "Dt. 15.10.2024"
```

> You'd need a different regex for every supplier. It breaks the moment a supplier changes their template. AI reads the text the way a human would — it understands what "Invoice No" and "Inv#" both mean.

---

**Q: Why NOT use AI for validation?**

> Validation is math and format checks. Those need to be exact and auditable.

```
  ┌──────────────────────────────────┬──────────────────────────────────┐
  │  RULES  ✓                        │  AI  ✗                           │
  ├──────────────────────────────────┼──────────────────────────────────┤
  │  Same input → same result always │  May give different answers      │
  │  No API call — instant           │  Needs API call — adds latency   │
  │  Exact reason for every failure  │  Cannot be audited               │
  │  Never hallucinates              │  Can hallucinate                 │
  │  10000 + 1800 = 11800, always    │  May say "looks correct" when not│
  └──────────────────────────────────┴──────────────────────────────────┘
```

> If AI approves a wrong tax calculation, you have a GST compliance problem. Rules are deterministic — they cannot be wrong.

---

**Q: What LLM did you use and why Groq?**

> Groq with `openai/gpt-oss-20b`. Groq runs inference much faster than standard OpenAI — important because extraction is a synchronous API call right now. The model was chosen because it supports `json_mode` (forces clean JSON output) and has a 131k token context window — large enough for any invoice PDF.

---

**Q: What is json_mode and why is it critical here?**

> `json_mode` forces the LLM to return only valid JSON — no markdown, no explanation text, no code fences. Without it:

```
  LLM might return:
  ─────────────────────────────────────────────────────
  "Here is the extracted invoice data:
  ```json
  { "invoiceNumber": "INV-001" }
  ```
  Hope this helps!"
  ─────────────────────────────────────────────────────

  With json_mode:
  ─────────────────────────────────────────────────────
  { "invoiceNumber": "INV-001", "supplierName": "...", ... }
  ─────────────────────────────────────────────────────
```

> The first breaks JSON parsing. The second maps directly to the Java object. `json_mode` is non-negotiable for structured extraction.

---

**Q: What happens if AI extraction fails?**

> The invoice status moves to `FAILED`. The extraction attempt is recorded in the `extraction_runs` table with status `FAILED` and the error message. The original PDF is still on disk. The user can call `POST /{id}/extract` again to retry — nothing is lost.

---

**Q: What prompt do you use for extraction?**

> The prompt instructs the LLM to act as an invoice extraction assistant, lists the exact field names it must return (`invoiceNumber`, `supplierName`, `supplierGstin`, `invoiceDate`, `taxableAmount`, `taxAmount`, `totalAmount`), tells it to use `null` for missing fields, and explicitly says — return only the JSON object, no explanation, no markdown. The raw PDF text is appended at the end.

---

## Section 4 — Validation

---

**Q: Explain the 6 validation rules.**

```
  ┌──────────────────────┬──────────┬────────────────────────────────────────┐
  │ Rule                 │ Severity │ What it checks                         │
  ├──────────────────────┼──────────┼────────────────────────────────────────┤
  │ RequiredFields       │ HIGH     │ invoiceNumber, supplierName,           │
  │                      │          │ invoiceDate, totalAmount — none null   │
  ├──────────────────────┼──────────┼────────────────────────────────────────┤
  │ TaxCalculation       │ HIGH     │ taxableAmount + taxAmount = total      │
  │                      │          │ tolerance ±0.01 for rounding           │
  ├──────────────────────┼──────────┼────────────────────────────────────────┤
  │ GstRate              │ MEDIUM   │ effective GST rate must be             │
  │                      │          │ 0%, 5%, 12%, 18%, or 28%              │
  ├──────────────────────┼──────────┼────────────────────────────────────────┤
  │ GstinFormat          │ MEDIUM   │ must match Indian GSTIN pattern        │
  │                      │          │ e.g. 29ABCDE1234F1Z5                   │
  ├──────────────────────┼──────────┼────────────────────────────────────────┤
  │ InvoiceDate          │ HIGH/LOW │ not in future → HIGH                   │
  │                      │          │ older than 1 year → LOW                │
  ├──────────────────────┼──────────┼────────────────────────────────────────┤
  │ DuplicateInvoice     │ CRITICAL │ same invoiceNumber + supplierGstin     │
  │                      │          │ must not already exist in DB           │
  └──────────────────────┴──────────┴────────────────────────────────────────┘
```

---

**Q: How is the ValidationEngine designed? What pattern does it use?**

> Strategy pattern. Every rule implements a `ValidationRule` interface with one method — `validate(Invoice)` returning a list of findings. The engine gets all rules injected by Spring as a `List<ValidationRule>`, runs them all, collects findings, saves them, and updates the invoice status.

```
  ValidationRule (interface)
       │
       ├── RequiredFieldsRule
       ├── TaxCalculationRule
       ├── GstRateRule
       ├── GstinFormatRule
       ├── InvoiceDateRule
       └── DuplicateInvoiceRule
```

> Adding a new rule = create one new class that implements `ValidationRule`. The engine picks it up automatically via Spring's dependency injection. Zero changes to existing code.

---

**Q: What is a ValidationFinding and what states can it be in?**

> A finding is a row in `validation_findings` — one row per rule violation per invoice.

```
  Fields:   ruleCode · severity · message · status · invoiceId

  Status lifecycle:
  OPEN  ──►  ACKNOWLEDGED  ──►  RESOLVED
    │
    └──────────────────────────► IGNORED
```

> A reviewer can mark a finding IGNORED if it's a known exception — for example, an old invoice being processed late for a valid reason.

---

**Q: What is the severity hierarchy?**

```
  CRITICAL  →  must be addressed — e.g. duplicate invoice
  HIGH      →  serious issue — e.g. tax math wrong, required field missing
  MEDIUM    →  moderate — e.g. wrong GST rate, bad GSTIN format
  LOW       →  informational — e.g. invoice is old but not invalid
```

---

## Section 5 — Security & Multi-tenancy

---

**Q: How does authentication work end to end?**

```
  REGISTER                              LOGIN
  ────────────────────────────          ────────────────────────────
  POST /auth/register                   POST /auth/login
       │                                     │
       ▼                                     ▼
  Create Organization                   Load user by email
  Create ADMIN User                     Verify BCrypt password
  Hash password (BCrypt)                     │
       │                                     ▼
       └──────────────────────────────► Generate JWT token
                                        { email, role, orgId }
                                             │
                                             ▼
                                        Return token to client
```

---

**Q: What is inside the JWT token?**

```
  ┌─────────────────────────────────────────────────────┐
  │  Header     { "alg": "HS256", "typ": "JWT" }        │
  ├─────────────────────────────────────────────────────┤
  │  Payload    { "sub": "user@email.com",              │
  │               "role": "ADMIN",                      │
  │               "orgId": 1,                           │
  │               "exp": 1735689600 }                   │
  ├─────────────────────────────────────────────────────┤
  │  Signature  HMAC-SHA256(header + payload, secret)   │
  └─────────────────────────────────────────────────────┘
```

> The `orgId` claim is what makes multi-tenancy work. It's embedded at login and cannot be changed without invalidating the signature.

---

**Q: How does every secured request get processed?**

```
  Client sends:  Authorization: Bearer <token>
                      │
                      ▼
               JwtAuthFilter (runs on every request)
                      │
                      ├── extract token from header
                      ├── validate signature + expiry
                      ├── extract email from token
                      ├── load User from DB
                      └── set User in SecurityContextHolder
                      │
                      ▼
               Controller method runs
               @AuthenticationPrincipal User user
                      │
                      └── user.getOrganization().getId()
                          used in every DB query
```

---

**Q: How is multi-tenancy enforced?**

> Three layers:

```
  Layer 1 — Token      orgId is embedded in JWT at login
  Layer 2 — Query      every repository method uses findByIdAndOrganizationId
  Layer 3 — Source     orgId always comes from @AuthenticationPrincipal,
                       never from a request parameter
```

> Even if someone sends another org's invoice ID in the URL, the query returns nothing — because the org doesn't match. There is no way to access another org's data.

---

**Q: What roles exist and what can each role do?**

```
  ┌──────────┬──────────────────────────────────────────────────────┐
  │ ADMIN    │  upload · extract · validate · approve · reject      │
  │          │  update findings · view reports · manage users       │
  ├──────────┼──────────────────────────────────────────────────────┤
  │ REVIEWER │  validate · approve · reject · update findings       │
  ├──────────┼──────────────────────────────────────────────────────┤
  │ VIEWER   │  read only — view invoices, findings, reports        │
  └──────────┴──────────────────────────────────────────────────────┘
```

---

**Q: What Spring Security components did you configure?**

```
  SecurityFilterChain    →  URL rules, STATELESS session, CSRF disabled
  JwtAuthFilter          →  OncePerRequestFilter, validates token, sets context
  UserDetailsService     →  loads User by email from DB for authentication
  BCryptPasswordEncoder  →  hashes on register, verifies on login
  AuthenticationManager  →  used in AuthService.login() to authenticate
```

---

## Section 6 — Real Challenges & How They Were Solved

---

**Challenge 1 — Unstructured PDF data**

```
  ❌ Problem
     Every supplier's PDF has a different layout.
     Regex and positional parsing break on every new supplier or template change.
     Maintenance becomes a full-time job.

  ✅ Solution
     Use an LLM with a structured prompt + json_mode.
     The prompt asks for specific fields by semantic meaning, not position.
     Works on any PDF layout without any code changes.
     New supplier = zero engineering effort.
```

---

**Challenge 2 — AI hallucination risk in validation**

```
  ❌ Problem
     If AI validates tax calculations, it might say "looks correct"
     even when the math is wrong. That's a GST compliance risk.
     AI is non-deterministic — same invoice might pass one day, fail the next.

  ✅ Solution
     AI is never used for validation. Period.
     All 6 rules are pure deterministic code.
     Same input always gives the same result.
     Every failure has an exact, auditable reason.
     AI is only used where ambiguity exists — reading text and explaining findings.
```

---

**Challenge 3 — Multi-tenancy data isolation**

```
  ❌ Problem
     Multiple companies share the same database.
     Company A must never see Company B's invoices — even by accident.
     Passing org_id as a request parameter is unsafe — anyone can change it.

  ✅ Solution
     org_id is embedded in the JWT token at login time.
     Every DB query is scoped: findByIdAndOrganizationId(id, orgId).
     orgId always comes from @AuthenticationPrincipal — never from the request.
     Even if someone guesses another org's invoice ID, the query returns 404.
```

---

**Challenge 4 — Spring Boot 4 breaking changes**

```
  ❌ Problem 1
     DaoAuthenticationProvider.setUserDetailsService() no longer works.
     App starts fine but login silently fails — no error, just wrong behavior.

  ✅ Solution
     Pass UserDetailsService in the constructor:
     new DaoAuthenticationProvider(userDetailsService)

  ❌ Problem 2
     @RequestParam with an enum type silently returns null when value is invalid.
     No exception thrown — query runs with null, returns wrong results.

  ✅ Solution
     Accept String param, call InvoiceStatus.valueOf() manually.
     Throws IllegalArgumentException → GlobalExceptionHandler maps it to 400.
```

---

**Challenge 5 — LLM model availability on Groq**

```
  ❌ Problem
     Groq account had only 11 models available.
     Most free models don't support json_mode.
     Initial model name in config was wrong — caused 404 API errors.

  ✅ Solution
     Checked available models via Groq API.
     Switched to openai/gpt-oss-20b:
     — supports json_mode
     — 131k context window (handles large PDFs)
     — negligible cost for invoice-scale usage
```

---

**Challenge 6 — JWT secret key length**

```
  ❌ Problem
     HMAC-SHA256 requires a minimum 32-character secret key.
     Short secrets cause a WeakKeyException at startup.

  ✅ Solution
     Used a 64-character secret in application.properties.
     In production this should live in AWS Secrets Manager or an env variable —
     never hardcoded, never committed to version control.
```

---

## Section 7 — Design Decisions

---

**Q: Why store PDFs on disk instead of in the database?**

> PDFs are binary blobs. Storing them in a DB bloats the database, slows down every query (even unrelated ones), and makes backups huge. The right place for files is file storage — local disk now, S3 in production. The DB stores only the metadata and a UUID key that points to the file.

---

**Q: Why is each pipeline step a separate API call?**

> Three reasons:
> - Each step fails independently — extraction failure doesn't lose the upload
> - Each step retries independently — re-extract without re-uploading the PDF
> - Long-running steps (AI call) don't block a single HTTP request indefinitely

> One big endpoint would be fragile — any failure loses all progress.

---

**Q: Why Flyway for DB migrations?**

> Every schema change is a versioned SQL file (V1 through V7). The DB evolves with the code. No manual scripts, no "remember to run this SQL". Flyway runs automatically on startup and applies any pending migrations. You can always see exactly what changed and when.

---

**Q: How would you scale this to production?**

```
  Current (dev)                    Production (AWS)
  ──────────────────────────────────────────────────────────
  PDF on local disk           →    AWS S3
  Groq LLM — sync call        →    async: SQS queue + worker
  Single Spring Boot instance →    ECS containers, load balanced
  Postgres in Docker          →    AWS RDS (Multi-AZ)
  JWT secret in .properties   →    AWS Secrets Manager
  No monitoring               →    CloudWatch + alerts
```

---

**Q: What would you build next?**

```
  1. Async extraction    move AI call to background job, poll for status
  2. Audit log           every status change logged with timestamp + user
  3. Email notifications notify reviewer when invoice is ready
  4. Bulk upload         ZIP of PDFs, process all in parallel
  5. Analytics dashboard approval rates, most common failure rules, trends
```

---

## Section 8 — Tricky Follow-up Questions

---

**Q: What if two users from the same org upload the same invoice simultaneously?**

> The `DuplicateInvoiceRule` catches this at validation time — it checks the DB for the same `invoiceNumber` + `supplierGstin` combination. The second one will get a CRITICAL finding. For true race conditions at the DB level, a unique constraint on `(invoice_number, supplier_gstin, org_id)` would be the right fix.

---

**Q: Is the JWT token stateless? What if you need to revoke it?**

> Yes, it's stateless — the server stores no session. The downside is you can't revoke a token before it expires. The current expiry is 24 hours. For revocation, you'd add a token blacklist in Redis — store revoked token IDs until their expiry time. That's a production concern not implemented here.

---

**Q: What if the LLM extracts a wrong value — say, wrong total amount?**

> That's exactly what the `TaxCalculationRule` catches. If the LLM misreads `11800` as `1180`, the rule will flag it as HIGH severity because `taxableAmount + taxAmount ≠ totalAmount`. The reviewer sees the finding, can check the original PDF, and rejects the invoice. The system is designed so AI mistakes are caught by rules.

---

**Q: Why not use Spring Data's @Query annotation instead of method name queries?**

> Method name queries like `findByIdAndOrganizationId` are readable, type-safe, and don't need SQL strings. For simple scoped lookups they're cleaner. `@Query` is better when you need complex joins, aggregations, or native SQL — like in `ReportService` where you need counts and sums grouped by status.

---

**Q: How does BCrypt work and why is it better than MD5 or SHA?**

> BCrypt is a slow hashing algorithm by design. It has a cost factor — the more rounds, the slower it is. MD5 and SHA are fast, which makes them easy to brute-force with GPU attacks. BCrypt takes ~100ms per hash — fast enough for login, too slow for bulk cracking. It also generates a unique salt per password, so two users with the same password get different hashes.

---

## Section 9 — Quick Fire

---

**Q: What is OncePerRequestFilter?**
> A Spring filter that runs exactly once per HTTP request. Used for `JwtAuthFilter` to avoid processing the token twice in complex filter chains.

**Q: What is @AuthenticationPrincipal?**
> Injects the authenticated user object directly into a controller method. Avoids manually calling `SecurityContextHolder.getContext().getAuthentication()`.

**Q: What is STATELESS session in Spring Security?**
> No HTTP session is created on the server. Every request is authenticated independently via the JWT token. Makes the API horizontally scalable — any server can handle any request.

**Q: What is CSRF and why is it disabled here?**
> CSRF (Cross-Site Request Forgery) attacks exploit browser cookie-based sessions. Since this API uses JWT in the Authorization header (not cookies), CSRF is not a threat. Disabling it removes unnecessary overhead.

**Q: What is the difference between GSTIN and invoice number?**
> GSTIN is the government-issued 15-character tax ID of the supplier. Invoice number is the supplier's own internal reference for that bill. Both together uniquely identify a transaction — same invoice number from two different suppliers is not a duplicate.

**Q: What is PDFBox?**
> An Apache library that reads PDF files and extracts the raw text content. It handles the binary PDF format so the application gets plain text that can be sent to the LLM.

**Q: What is Flyway?**
> A DB migration tool. Each migration is a versioned SQL file. Flyway runs on startup and applies any unapplied migrations in order. Keeps the DB schema in sync with the code across all environments.
