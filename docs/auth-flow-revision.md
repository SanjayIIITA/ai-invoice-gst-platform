# Auth Flow — Quick Revision Guide

---

## The Big Picture

```
User ──► Register/Login ──► Get Token ──► Send Token with every request ──► Access API
```

Three things this system does:
1. **Authentication** — who are you? (email + password)
2. **Authorization** — what can you do? (ADMIN / REVIEWER / VIEWER)
3. **Isolation** — you only see your company's data (org_id)

---

## Files at a Glance

```
WHO HANDLES WHAT
─────────────────────────────────────────────────────
RegisterRequest.java   → validates input before it reaches service
LoginRequest.java      → validates input before it reaches service
AuthController.java    → receives HTTP request, calls AuthService
AuthService.java       → business logic: save user, verify password
JwtService.java        → creates tokens, reads tokens, validates tokens
JwtAuthFilter.java     → runs before every request, checks the token
SecurityConfig.java    → defines the rules: who can access what
User.java              → the user entity, implements UserDetails
Organization.java      → the company/tenant entity
Role.java              → ADMIN, REVIEWER, VIEWER
```

---

## What is a JWT Token

A JWT looks like this:
```
eyJhbGciOiJIUzI1NiJ9  .  eyJzdWIiOiJhZG1pbkBhY21lLmNvbSJ9  .  SflKxwRJSMeKKF2QT4fw
      HEADER                        PAYLOAD                           SIGNATURE
```

Our token payload contains:
```json
{
  "sub":   "admin@acme.com",
  "role":  "ADMIN",
  "orgId": 1,
  "iat":   1700000000,
  "exp":   1700086400
}
```

3 things to remember about JWT:
- **NOT encrypted** — anyone can decode the payload (base64)
- **Tamper-proof** — signature breaks if payload is changed
- **Server never stores it** — client stores it, server only verifies it

---

## Flow 1 — Register

### Step by Step

```
POST /api/v1/auth/register
{
  "email":            "admin@acme.com",
  "password":         "password123",
  "fullName":         "Admin User",
  "organizationName": "Acme Corp"
}
```

```
Step 1  →  @Valid checks input (email format, password min 8 chars)
Step 2  →  AuthController receives request, calls AuthService.register()
Step 3  →  AuthService checks: is email already taken? if yes → 400 error
Step 4  →  Create Organization row in DB  (Acme Corp, id = 1)
Step 5  →  Create User row in DB
             email         = "admin@acme.com"
             password_hash = BCrypt("password123")  → "$2a$10$xyz..."
             role          = ADMIN  (first user of org is always ADMIN)
             org_id        = 1
Step 6  →  JwtService.generate(user) creates a signed token
Step 7  →  Return token + user info to client
```

### Response
```json
{
  "token":    "eyJhbGciOiJIUzI1NiJ9...",
  "email":    "admin@acme.com",
  "fullName": "Admin User",
  "role":     "ADMIN",
  "orgId":    1
}
```

### Key Points
- `@Transactional` — if user save fails after org save, org is also rolled back
- BCrypt is one-way — you can never reverse the hash back to the password
- Every BCrypt call produces a different hash (random salt) but `matches()` always works

---

## Flow 2 — Login

### Step by Step

```
POST /api/v1/auth/login
{
  "email":    "admin@acme.com",
  "password": "password123"
}
```

```
Step 1  →  AuthController receives request, calls AuthService.login()
Step 2  →  authenticationManager.authenticate(email, password)
             internally:
               a. loads User from DB by email
               b. calls BCrypt.matches("password123", storedHash)
               c. if match  → success, continue
               d. if no match → BadCredentialsException → 401 Unauthorized
Step 3  →  Load User from DB again (authenticate() does not return the user)
Step 4  →  JwtService.generate(user) creates a fresh token
Step 5  →  Return token + user info to client
```

### Key Points
- `authenticationManager` is a Spring bean defined in `SecurityConfig`
- It uses `UserDetailsService` (also in SecurityConfig) to load the user by email
- `UserDetailsService` calls `userRepository.findByEmail(email)`

---

## Flow 3 — Secured Request

### Step by Step

```
GET /api/v1/invoices
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

```
Step 1  →  JwtAuthFilter intercepts the request (runs before controller)
Step 2  →  Read "Authorization" header
Step 3  →  Does it start with "Bearer "?
             NO  → skip, Spring Security returns 401
             YES → extract token (remove "Bearer " prefix)
Step 4  →  jwtService.isValid(token)
             checks signature + expiry
             NO  → skip, Spring Security returns 401
             YES → continue
Step 5  →  jwtService.extractEmail(token) → "admin@acme.com"
Step 6  →  userRepository.findByEmail("admin@acme.com") → loads User from DB
Step 7  →  Store User in SecurityContextHolder
             (Spring's holder for "who is logged in during this request")
Step 8  →  Request continues to SecurityConfig rules
Step 9  →  SecurityConfig checks: does this URL need a specific role?
             if user has the role → continue to controller
             if not              → 403 Forbidden
Step 10 →  Controller runs
             @AuthenticationPrincipal User user
             → pulls the User object stored in Step 7
             → no extra DB call needed
Step 11 →  Service uses user.getOrganization().getId() to scope DB query
```

---

## Token Creation — JwtService.generate()

```java
Jwts.builder()
    .subject(user.getEmail())                       // who is this user
    .claim("role",  user.getRole().name())          // what role do they have
    .claim("orgId", user.getOrganization().getId()) // which org do they belong to
    .issuedAt(new Date())                           // created at
    .expiration(new Date(now + 86400000))           // expires in 24 hours
    .signWith(key)                                  // sign with HMAC-SHA256
    .compact()                                      // produce "eyJ..." string
```

---

## Token Validation — JwtService.isValid()

```java
try {
    Jwts.parser()
        .verifyWith(key)          // check signature using our secret key
        .build()
        .parseSignedClaims(token) // throws if expired or tampered
        .getPayload();            // returns claims if valid
    return true;
} catch (Exception e) {
    return false;                 // expired / tampered / wrong key → false
}
```

---

## SecurityConfig — 4 Beans, 4 Responsibilities

```
filterChain()           → URL rules: who can access what
userDetailsService()    → how to load a user by email (used during login)
authenticationManager() → verifies email + password (used in AuthService.login)
passwordEncoder()       → BCrypt: encode on save, matches() on login
```

### URL Rules (in order, first match wins)
```
/api/v1/auth/**              → PUBLIC  (no token needed)
/api/v1/health               → PUBLIC  (no token needed)
POST .../approve or /reject  → ADMIN or REVIEWER only
PATCH .../findings/*         → ADMIN or REVIEWER only
everything else              → any authenticated user (valid token)
```

---

## User.java — Why implements UserDetails

Spring Security does not know our `User` class.
`UserDetails` is Spring's interface for "a user that can be authenticated."

By implementing it, we provide 3 things Spring needs:

```
getUsername()     → returns email  (Spring uses this to find the user)
getPassword()     → returns BCrypt hash  (Spring uses this to verify login)
getAuthorities()  → returns ["ROLE_ADMIN"]  (Spring uses this for role checks)
```

---

## Role-Based Access — How ROLE_ Prefix Works

```
User.getAuthorities() returns:
  Role.ADMIN    → "ROLE_ADMIN"
  Role.REVIEWER → "ROLE_REVIEWER"
  Role.VIEWER   → "ROLE_VIEWER"

SecurityConfig uses:
  .hasAnyRole("ADMIN", "REVIEWER")
  → Spring automatically looks for "ROLE_ADMIN" and "ROLE_REVIEWER"
  → That is why we add "ROLE_" prefix in getAuthorities()
```

---

## Multi-Tenancy — How Org Isolation Works

```
Every invoice has org_id
Every user    has org_id

When user makes a request:
  1. JwtAuthFilter loads User from DB
  2. Controller gets User via @AuthenticationPrincipal
  3. orgId = user.getOrganization().getId()
  4. All DB queries use orgId as a filter

Result:
  Company A (orgId=1) → only sees invoices where org_id = 1
  Company B (orgId=2) → only sees invoices where org_id = 2
  Even if Company A guesses an invoice ID of Company B → DB returns empty
```

---

## SecurityContextHolder — What It Is

Think of it as a **request-scoped box** that holds "who is logged in."

```
JwtAuthFilter  →  puts User into the box
SecurityConfig →  reads from the box to check roles
Controller     →  reads from the box via @AuthenticationPrincipal
End of request →  box is cleared automatically
```

---

## Common Questions

**Q: Why do we load the user from DB in JwtAuthFilter if the token already has email, role, orgId?**
A: The token could be valid but the user might have been deleted or their role changed.
   Loading from DB ensures we always have the current state.

**Q: Why is CSRF disabled?**
A: CSRF attacks work by tricking a browser into sending a cookie automatically.
   We use tokens in the Authorization header — the browser never sends them automatically.
   So CSRF is not a threat here.

**Q: Why STATELESS session policy?**
A: Prevents Spring from creating an HttpSession.
   With JWT, the server should never remember anything between requests.
   Every request is self-contained with its token.

**Q: What happens when the token expires?**
A: `jwtService.isValid()` returns false → JwtAuthFilter skips setting auth → Spring returns 401.
   The client must call `/auth/login` again to get a new token.

**Q: Why is the first registered user always ADMIN?**
A: Whoever creates the organization owns it.
   They can then create other users with REVIEWER or VIEWER roles.

---

## Quick Reference — Curl Commands

```bash
# Register
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@acme.com","password":"password123","fullName":"Admin","organizationName":"Acme Corp"}'

# Login
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@acme.com","password":"password123"}'

# Use token
curl http://localhost:8080/api/v1/invoices \
  -H "Authorization: Bearer <paste token here>"
```
