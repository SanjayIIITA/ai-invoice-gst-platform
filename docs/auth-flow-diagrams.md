# Auth Flow — Diagrams

---

## 1. Overall Picture

```
┌─────────┐                                          ┌─────────────┐
│         │  ① POST /auth/register or /auth/login    │             │
│         │ ────────────────────────────────────────►│             │
│ CLIENT  │                                          │   SERVER    │
│         │  ② { token, email, role, orgId }         │             │
│         │ ◄────────────────────────────────────────│             │
└─────────┘                                          └─────────────┘

┌─────────┐                                          ┌─────────────┐
│         │  ③ GET /invoices                         │             │
│         │     Authorization: Bearer eyJhbG...      │             │
│         │ ────────────────────────────────────────►│             │
│ CLIENT  │                                          │   SERVER    │
│         │  ④ [ invoices of their org only ]        │             │
│         │ ◄────────────────────────────────────────│             │
└─────────┘                                          └─────────────┘
```

---

## 2. Register Flow

```
  Client                AuthController         AuthService              DB
    │                        │                      │                    │
    │  POST /auth/register   │                      │                    │
    │  {email, password,     │                      │                    │
    │   fullName, orgName}   │                      │                    │
    │ ──────────────────────►│                      │                    │
    │                        │  @Valid checks input │                    │
    │                        │  ─────────────────── │                    │
    │                        │  authService         │                    │
    │                        │  .register(request)  │                    │
    │                        │ ────────────────────►│                    │
    │                        │                      │  email taken?      │
    │                        │                      │ ──────────────────►│
    │                        │                      │  NO ◄──────────────│
    │                        │                      │                    │
    │                        │                      │  save Organization │
    │                        │                      │ ──────────────────►│
    │                        │                      │  id=1 ◄────────────│
    │                        │                      │                    │
    │                        │                      │  BCrypt(password)  │
    │                        │                      │  save User         │
    │                        │                      │  role = ADMIN      │
    │                        │                      │  org_id = 1        │
    │                        │                      │ ──────────────────►│
    │                        │                      │  saved ◄───────────│
    │                        │                      │                    │
    │                        │                      │  generate token    │
    │                        │                      │  ──────────────── ►│JwtService
    │                        │                      │  "eyJhbG..." ◄─────│
    │                        │                      │                    │
    │  {token, email,        │                      │                    │
    │   role, orgId}         │◄─────────────────────│                    │
    │ ◄──────────────────────│                      │                    │
    │                        │                      │                    │
```

---

## 3. Login Flow

```
  Client                AuthController         AuthService         AuthenticationManager
    │                        │                      │                       │
    │  POST /auth/login      │                      │                       │
    │  {email, password}     │                      │                       │
    │ ──────────────────────►│                      │                       │
    │                        │  authService         │                       │
    │                        │  .login(request)     │                       │
    │                        │ ────────────────────►│                       │
    │                        │                      │  authenticate         │
    │                        │                      │  (email, password)    │
    │                        │                      │ ─────────────────────►│
    │                        │                      │                       │  load user from DB
    │                        │                      │                       │  BCrypt.matches()
    │                        │                      │                       │
    │                        │                      │  ✓ success ◄──────────│
    │                        │                      │  (or 401 if wrong)    │
    │                        │                      │                       │
    │                        │                      │  load user from DB    │
    │                        │                      │  generate token       │
    │                        │                      │                       │
    │  {token, email,        │◄─────────────────────│                       │
    │   role, orgId}         │                      │                       │
    │ ◄──────────────────────│                      │                       │
    │                        │                      │                       │
```

---

## 4. Secured Request Flow

```
  Client          JwtAuthFilter       SecurityConfig        Controller         Service
    │                  │                    │                    │                │
    │  GET /invoices   │                    │                    │                │
    │  Bearer token    │                    │                    │                │
    │ ────────────────►│                    │                    │                │
    │                  │                    │                    │                │
    │                  │  read header       │                    │                │
    │                  │  extract token     │                    │                │
    │                  │  validate token    │                    │                │
    │                  │                    │                    │                │
    │                  │  ✗ invalid ──────────────────────────────────► 401      │
    │                  │                    │                    │                │
    │                  │  ✓ valid           │                    │                │
    │                  │  extract email     │                    │                │
    │                  │  load User from DB │                    │                │
    │                  │  set SecurityContext                    │                │
    │                  │ ──────────────────►│                    │                │
    │                  │                    │  check role rules  │                │
    │                  │                    │                    │                │
    │                  │                    │  ✗ wrong role ──────────────► 403  │
    │                  │                    │                    │                │
    │                  │                    │  ✓ role ok         │                │
    │                  │                    │ ──────────────────►│                │
    │                  │                    │                    │  @Authentication│
    │                  │                    │                    │  Principal User │
    │                  │                    │                    │ ───────────────►│
    │                  │                    │                    │                │  query DB
    │                  │                    │                    │                │  by orgId
    │  [ invoices ]    │                    │                    │◄───────────────│
    │ ◄────────────────────────────────────────────────────────────               │
    │                  │                    │                    │                │
```

---

## 5. JWT Token Structure

```
  eyJhbGciOiJIUzI1NiJ9 . eyJzdWIiOiJhZG1pbkBhY21lLmNvbSJ9 . SflKxwRJSMeKKF2QT4fw
  ─────────────────────   ──────────────────────────────────   ────────────────────
        HEADER                        PAYLOAD                       SIGNATURE
                                                                         │
                              ┌──────────────────┐                       │
                              │  sub:   email     │                       │
                              │  role:  ADMIN     │    signed with        │
                              │  orgId: 1         │◄──────────────────────┘
                              │  iat:   issued at │    secret key
                              │  exp:   expiry    │
                              └──────────────────┘
```

---

## 6. SecurityConfig — URL Rules

```
  Incoming Request
        │
        ▼
  ┌─────────────────────────────────────────────────────────┐
  │                    SecurityConfig                        │
  │                                                         │
  │  /api/v1/auth/**          ──────────────► ALLOW (public)│
  │  /api/v1/health           ──────────────► ALLOW (public)│
  │                                                         │
  │  POST .../approve         ──► ADMIN or REVIEWER? ──► ✓ │
  │  POST .../reject          ──► ADMIN or REVIEWER? ──► ✓ │
  │  PATCH .../findings/*     ──► ADMIN or REVIEWER? ──► ✓ │
  │                                           │                │
  │                                           └──────────► ✗ 403
  │                                                         │
  │  everything else          ──► valid token? ────────► ✓ │
  │                                           │                │
  │                                           └──────────► ✗ 401
  └─────────────────────────────────────────────────────────┘
```

---

## 7. Role-Based Access

```
  ┌──────────┬────────────────────────────────────────────────────────┐
  │   Role   │  What they can do                                      │
  ├──────────┼────────────────────────────────────────────────────────┤
  │  ADMIN   │  upload, extract, validate, approve, reject, findings  │
  ├──────────┼────────────────────────────────────────────────────────┤
  │ REVIEWER │  approve, reject, update findings, view                │
  ├──────────┼────────────────────────────────────────────────────────┤
  │  VIEWER  │  view invoices, findings, reports only                 │
  └──────────┴────────────────────────────────────────────────────────┘

  How Spring checks:

  User.getAuthorities()  ──►  "ROLE_ADMIN"
                                    │
                         SecurityConfig
                         hasAnyRole("ADMIN") ──► looks for "ROLE_ADMIN" ──► ✓
```

---

## 8. Multi-Tenancy — Org Isolation

```
  ┌─────────────────────────────────────────────────────────────┐
  │  organizations                                              │
  │  ┌────┬──────────┐                                         │
  │  │ id │ name     │                                         │
  │  ├────┼──────────┤                                         │
  │  │  1 │ Acme Corp│◄──────────────────────────────┐        │
  │  │  2 │ Beta Ltd │◄──────────────────────┐       │        │
  │  └────┴──────────┘                       │       │        │
  │                                          │       │        │
  │  users                                   │       │        │
  │  ┌────┬───────────────────┬────────┐     │       │        │
  │  │ id │ email             │ org_id │     │       │        │
  │  ├────┼───────────────────┼────────┤     │       │        │
  │  │  1 │ admin@acme.com    │   1    │─────┘       │        │
  │  │  2 │ user@beta.com     │   2    │─────────────┘        │
  │  └────┴───────────────────┴────────┘                      │
  │                                                            │
  │  invoices                                                  │
  │  ┌────┬──────────────┬────────┐                           │
  │  │ id │ invoice_no   │ org_id │                           │
  │  ├────┼──────────────┼────────┤                           │
  │  │  1 │ INV-2024-001 │   1    │  ← only Acme can see this │
  │  │  2 │ INV-2024-002 │   1    │  ← only Acme can see this │
  │  │  3 │ INV-2025-001 │   2    │  ← only Beta can see this │
  │  └────┴──────────────┴────────┘                           │
  └─────────────────────────────────────────────────────────────┘

  Query:  findByIdAndOrganizationId(invoiceId, orgId)
          ─────────────────────────────────────────────
          Acme user requests invoice id=3 → returns empty → 404
```

---

## 9. SecurityContextHolder

```
  ┌──────────────────────────────────────────────────────────────┐
  │                    One HTTP Request                          │
  │                                                              │
  │   JwtAuthFilter                                              │
  │       │  loads User from DB                                  │
  │       │  stores in SecurityContextHolder                     │
  │       ▼                                                      │
  │   ┌──────────────────────────┐                              │
  │   │   SecurityContextHolder  │                              │
  │   │   { authenticated User } │                              │
  │   └──────────────────────────┘                              │
  │       │                    │                                 │
  │       ▼                    ▼                                 │
  │   SecurityConfig       Controller                            │
  │   reads role           reads User via                        │
  │   for access check     @AuthenticationPrincipal              │
  │                                                              │
  │   ── end of request ── box is cleared ───────────────────── │
  └──────────────────────────────────────────────────────────────┘
```

---

## 10. BCrypt Password Flow

```
  REGISTER
  ─────────────────────────────────────────────────────
  "password123"  ──►  BCrypt.encode()  ──►  "$2a$10$xyz..."
                                                │
                                           saved in DB
                                           (never plain text)

  LOGIN
  ─────────────────────────────────────────────────────
  "password123"  ──►  BCrypt.matches("password123", "$2a$10$xyz...")
                                                │
                                           true  ──► login success
                                           false ──► 401 Unauthorized
```
