# JWT Authentication & Security — Detailed Documentation

## Table of Contents
1. [What Problem Are We Solving](#1-what-problem-are-we-solving)
2. [What is JWT](#2-what-is-jwt)
3. [Project Structure — Auth Files](#3-project-structure--auth-files)
4. [Database Tables](#4-database-tables)
5. [Entity Layer — User & Organization](#5-entity-layer--user--organization)
6. [Flow 1 — Register](#6-flow-1--register)
7. [Flow 2 — Login](#7-flow-2--login)
8. [Flow 3 — Secured Request](#8-flow-3--secured-request)
9. [SecurityConfig — The Rule Book](#9-securityconfig--the-rule-book)
10. [JwtService — Token Factory](#10-jwtservice--token-factory)
11. [JwtAuthFilter — The Gatekeeper](#11-jwtauthfilter--the-gatekeeper)
12. [Role-Based Access Control](#12-role-based-access-control)
13. [Multi-Tenancy — Org Isolation](#13-multi-tenancy--org-isolation)
14. [Complete Request Lifecycle](#14-complete-request-lifecycle)
15. [Why Each Decision Was Made](#15-why-each-decision-was-made)

---

## 1. What Problem Are We Solving

Without authentication, anyone can call our API:
```
GET  /api/v1/invoices            → returns ALL invoices of ALL companies
POST /api/v1/invoices/5/approve  → anyone can approve anything
```

We need to:
- Know **who** is making the request (Authentication)
- Know **what they are allowed to do** (Authorization)
- Ensure **Company A cannot see Company B's invoices** (Multi-tenancy)

JWT solves all three without storing any session on the server.

---

## 2. What is JWT

JWT stands for **JSON Web Token**. It is a string that looks like this:

```
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbkBhY21lLmNvbSIsInJvbGUiOiJBRE1JTiIsIm9yZ0lkIjoxfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c
```

It has 3 parts separated by dots:

```
HEADER . PAYLOAD . SIGNATURE
```

- **HEADER** — algorithm used to sign (HS256)
- **PAYLOAD** — the actual data (email, role, orgId, expiry) — base64 encoded, NOT encrypted
- **SIGNATURE** — HEADER + PAYLOAD signed with a secret key — proves nobody tampered with it

What our token payload contains:
```json
{
  "sub":   "admin@acme.com",
  "role":  "ADMIN",
  "orgId": 1,
  "iat":   1700000000,
  "exp":   1700086400
}
```

Key point: **The server never stores the token.**
The client stores it and sends it with every request.
The server only verifies the signature to trust it.

---

## 3. Project Structure — Auth Files

```
boundary/   (HTTP layer — what the outside world sees)
├── AuthController.java     → POST /auth/register, POST /auth/login
├── JwtAuthFilter.java      → intercepts every request, checks token
├── SecurityConfig.java     → defines security rules
├── RegisterRequest.java    → input validation for register
├── LoginRequest.java       → input validation for login
└── AuthResponse.java       → output: token + user info

control/    (business logic)
├── AuthService.java        → register and login logic
└── JwtService.java         → create and validate tokens

entity/     (database)
├── User.java               → maps to users table
├── Organization.java       → maps to organizations table
└── Role.java               → ADMIN, REVIEWER, VIEWER
```

---

## 4. Database Tables

```sql
-- Every company/tenant is an Organization
CREATE TABLE organizations (
    id         BIGSERIAL    PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,       -- "Acme Corp"
    created_at TIMESTAMPTZ  NOT NULL
);

-- Every user belongs to one Organization
CREATE TABLE users (
    id            BIGSERIAL    PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,  -- used as username
    password_hash VARCHAR(255) NOT NULL,          -- BCrypt hash, never plain text
    full_name     VARCHAR(255) NOT NULL,
    role          VARCHAR(50)  NOT NULL,          -- ADMIN / REVIEWER / VIEWER
    org_id        BIGINT       NOT NULL REFERENCES organizations(id),
    created_at    TIMESTAMPTZ  NOT NULL
);

-- Invoices are scoped to an org
ALTER TABLE invoices ADD COLUMN org_id BIGINT REFERENCES organizations(id);
```

---

## 5. Entity Layer — User & Organization

### Organization.java
```java
@Entity
@Table(name = "organizations")
public class Organization {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;   // "Acme Corp"
}
```
Simple entity. One row per company.
All invoices and users link back to this via `org_id`.

---

### User.java
```java
@Entity
@Table(name = "users")
public class User implements UserDetails {  // implements UserDetails — important!

    private String email;
    private String passwordHash;  // BCrypt hash stored here, never plain password
    private Role role;            // ADMIN, REVIEWER, or VIEWER

    @ManyToOne(fetch = FetchType.LAZY)
    private Organization organization;  // which company this user belongs to

    // Spring Security needs these 3 methods from UserDetails:

    @Override
    public String getUsername() { return email; }
    // Spring uses email as the "username" for authentication

    @Override
    public String getPassword() { return passwordHash; }
    // Spring uses this BCrypt hash to verify the login password

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
        // Role.ADMIN   → "ROLE_ADMIN"
        // Role.REVIEWER → "ROLE_REVIEWER"
        // Role.VIEWER  → "ROLE_VIEWER"
        // Spring Security requires the "ROLE_" prefix for hasRole() checks
    }
}
```

**Why `implements UserDetails`?**
Spring Security does not know about our `User` class by default.
By implementing `UserDetails`, we tell Spring:
"use this class as the authenticated user object and here is how to get the username, password, and roles from it."

---

### Role.java
```java
public enum Role {
    ADMIN,     // full access: upload, extract, validate, approve, reject
    REVIEWER,  // can approve, reject, update findings
    VIEWER     // read-only: view invoices, findings, reports
}
```

---

## 6. Flow 1 — Register

### What the client sends
```
POST /api/v1/auth/register
Content-Type: application/json

{
  "email":            "admin@acme.com",
  "password":         "password123",
  "fullName":         "Admin User",
  "organizationName": "Acme Corp"
}
```

---

### RegisterRequest.java — Input Validation
```java
public record RegisterRequest(
    @NotBlank @Email String email,             // must be valid email format
    @NotBlank @Size(min = 8) String password,  // minimum 8 characters
    @NotBlank String fullName,
    @NotBlank String organizationName
) {}
```
`@Valid` in the controller triggers these checks before the method even runs.
If any check fails, Spring automatically returns `400 Bad Request`.

---

### AuthController.java
```java
@PostMapping("/register")
public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
    // @Valid       → runs the validation annotations on RegisterRequest fields
    // @RequestBody → Spring parses the JSON body into a RegisterRequest object
    return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    // 201 Created is returned (not 200) because we created a new resource
}
```

---

### AuthService.register() — Step by Step
```java
@Transactional  // if anything fails midway, the whole thing rolls back — no partial saves
public AuthResponse register(RegisterRequest request) {

    // STEP 1: Check if email is already taken
    if (userRepository.existsByEmail(request.email())) {
        throw new IllegalArgumentException("Email already registered: " + request.email());
        // GlobalExceptionHandler catches this and returns 400 Bad Request
    }

    // STEP 2: Create the Organization first (user needs an org to belong to)
    Organization org = new Organization();
    org.setName(request.organizationName());  // "Acme Corp"
    org = organizationRepository.save(org);   // saved to DB, gets id = 1

    // STEP 3: Create the User
    User user = new User();
    user.setEmail(request.email());

    user.setPasswordHash(passwordEncoder.encode(request.password()));
    // passwordEncoder.encode("password123")
    //   → "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"
    // BCrypt is a one-way hash — you can NEVER reverse it back to "password123"
    // Every call to encode() produces a DIFFERENT hash (random salt each time)
    // But passwordEncoder.matches("password123", storedHash) always returns true

    user.setFullName(request.fullName());
    user.setRole(Role.ADMIN);    // first user of a new org is always ADMIN
    user.setOrganization(org);   // link user to the org we just created
    user = userRepository.save(user);  // saved to DB

    // STEP 4: Generate JWT token
    String token = jwtService.generate(user);

    // STEP 5: Return token + user info to the client
    return new AuthResponse(
        token,
        user.getEmail(),
        user.getFullName(),
        user.getRole().name(),
        user.getOrganization().getId()
    );
}
```

---

### JwtService.generate() — Creating the Token
```java
public String generate(User user) {
    return Jwts.builder()
        .subject(user.getEmail())                       // "admin@acme.com" — who is this user
        .claim("role",  user.getRole().name())          // "ADMIN" — stored inside the token
        .claim("orgId", user.getOrganization().getId()) // 1 — stored inside the token
        .issuedAt(new Date())                           // when was this token created
        .expiration(new Date(System.currentTimeMillis() + expirationMs))  // expires in 24 hours
        .signWith(key)                                  // sign with HMAC-SHA256 using our secret key
        .compact();                                     // produce the final "eyJ..." string
}
```

The secret key comes from `application.properties`:
```properties
app.jwt.secret=ai-invoice-gst-platform-secret-key-must-be-at-least-32-chars
app.jwt.expiration-ms=86400000   # 86400000ms = 24 hours
```

The key must be at least 32 characters for HMAC-SHA256.
If someone gets this key, they can forge tokens — keep it secret and never commit it to git.

---

### What the client gets back
```json
{
  "token":    "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbkBhY21lLmNvbSJ9.abc123",
  "email":    "admin@acme.com",
  "fullName": "Admin User",
  "role":     "ADMIN",
  "orgId":    1
}
```

The client must save this token and send it with every future request.

---

## 7. Flow 2 — Login

### What the client sends
```
POST /api/v1/auth/login
Content-Type: application/json

{
  "email":    "admin@acme.com",
  "password": "password123"
}
```

---

### AuthService.login() — Step by Step
```java
public AuthResponse login(LoginRequest request) {

    // STEP 1: Verify email + password
    authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(request.email(), request.password())
    );
    // What happens inside authenticate():
    //   a. Calls userDetailsService.loadUserByUsername("admin@acme.com")
    //   b. That calls userRepository.findByEmail("admin@acme.com") → loads User from DB
    //   c. Calls passwordEncoder.matches("password123", storedBCryptHash)
    //   d. If match  → authentication succeeds, continues to next line
    //   e. If no match → throws BadCredentialsException → Spring returns 401 Unauthorized

    // STEP 2: Load user from DB
    // authenticate() only tells us if credentials are valid — it does not return the User
    // So we fetch it separately to build the response
    User user = userRepository.findByEmail(request.email())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

    // STEP 3: Generate a fresh token and return
    return toResponse(jwtService.generate(user), user);
}
```

---

## 8. Flow 3 — Secured Request

After login, the client has a token. Every request must include it:

```
GET /api/v1/invoices
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

---

### JwtAuthFilter.java — Runs Before Every Request
```java
// OncePerRequestFilter guarantees this filter runs exactly once per HTTP request
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        // STEP 1: Read the Authorization header
        String header = request.getHeader("Authorization");

        // STEP 2: No header or wrong format → skip, pass to next filter
        // Spring Security will then reject it as 401 if the endpoint requires auth
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        // STEP 3: Extract the token — remove "Bearer " (7 characters)
        String token = header.substring(7);
        // "Bearer eyJhbGci..." → "eyJhbGci..."

        // STEP 4: Validate the token
        // isValid() checks: is the signature correct? is it expired?
        if (!jwtService.isValid(token)) {
            chain.doFilter(request, response);
            return;
            // Spring Security rejects the request as 401
        }

        // STEP 5: Extract email from inside the token
        String email = jwtService.extractEmail(token);
        // reads the "sub" claim from the token payload → "admin@acme.com"

        // STEP 6: Only set auth if not already authenticated (avoid double processing)
        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            // STEP 7: Load the full User object from DB
            UserDetails user = userRepository.findByEmail(email).orElse(null);

            if (user != null) {
                // STEP 8: Create an authentication token
                UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                        user,                  // principal — the full User object
                        null,                  // credentials — null (password not needed here)
                        user.getAuthorities()  // ["ROLE_ADMIN"]
                    );
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                // setDetails adds extra info like IP address — useful for audit logs

                // STEP 9: Store in SecurityContextHolder
                // This is Spring's way of saying "this request is authenticated as this user"
                // Any code in this request can now access the authenticated user
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }

        // STEP 10: Continue to the controller
        chain.doFilter(request, response);
    }
}
```

---

### JwtService — Parsing and Validating
```java
// Verify signature and read the claims from the token
public Claims parse(String token) {
    return Jwts.parser()
        .verifyWith(key)              // verify signature using our secret key
        .build()
        .parseSignedClaims(token)
        .getPayload();                // returns: sub, role, orgId, iat, exp
}

// Check if token is valid — correct signature AND not expired
public boolean isValid(String token) {
    try {
        parse(token);   // if this does not throw, token is valid
        return true;
    } catch (Exception e) {
        return false;   // expired, tampered, or wrong signature → false
    }
}

// Get the email (subject) from inside the token
public String extractEmail(String token) {
    return parse(token).getSubject();  // reads "sub" claim → "admin@acme.com"
}
```

---

### Controller — Getting the Authenticated User
```java
@GetMapping
public ResponseEntity<List<InvoiceResponse>> findAll(@AuthenticationPrincipal User user) {
    // @AuthenticationPrincipal pulls the User object from SecurityContextHolder
    // This is the same User object that JwtAuthFilter stored in STEP 9
    // No extra DB call needed here — already loaded in the filter

    Long orgId = user.getOrganization().getId();
    // use orgId to scope the query — user only sees their own org's invoices
    return ResponseEntity.ok(service.findAll(orgId));
}
```

---

## 9. SecurityConfig — The Rule Book

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    return http

        // RULE 1: Disable CSRF protection
        // CSRF attacks work by tricking a browser into sending a cookie
        // We use tokens in the Authorization header — CSRF is not applicable
        .csrf(AbstractHttpConfigurer::disable)

        // RULE 2: No server-side sessions
        // STATELESS means Spring will never create an HttpSession
        // Every request must carry its own token — nothing is remembered server-side
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

        // RULE 3: URL-level access rules — checked in order, first match wins
        .authorizeHttpRequests(auth -> auth

            // Public — no token needed
            .requestMatchers("/api/v1/auth/**", "/api/v1/health").permitAll()

            // Only ADMIN or REVIEWER can approve or reject invoices
            .requestMatchers(HttpMethod.POST,
                "/api/v1/invoices/*/approve",
                "/api/v1/invoices/*/reject").hasAnyRole("ADMIN", "REVIEWER")

            // Only ADMIN or REVIEWER can update findings
            .requestMatchers(HttpMethod.PATCH,
                "/api/v1/invoices/*/findings/*").hasAnyRole("ADMIN", "REVIEWER")

            // Everything else just needs a valid token (any role)
            .anyRequest().authenticated()
        )

        // RULE 4: Run JwtAuthFilter before Spring's default login filter
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
}

// Tells Spring how to load a user by email — used during login verification
@Bean
public UserDetailsService userDetailsService() {
    return email -> userRepository.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
}

// Used by AuthService.login() to verify email + password
@Bean
public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
    return config.getAuthenticationManager();
}

// BCrypt password hashing — used when saving and verifying passwords
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

---

## 10. JwtService — Token Factory

```java
@Service
public class JwtService {

    private final SecretKey key;      // the signing key — built from app.jwt.secret
    private final long expirationMs;  // 86400000 = 24 hours in milliseconds

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-ms}") long expirationMs) {
        // Convert the plain string secret into a cryptographic SecretKey object
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generate(User user) { ... }   // CREATE a token
    public Claims parse(String token) { ... }   // READ claims from a token
    public String extractEmail(String token) { ... }  // GET email from a token
    public boolean isValid(String token) { ... }      // CHECK if token is valid
}
```

Single responsibility: everything about JWT lives here.
No other class knows how tokens are created or parsed.

---

## 11. JwtAuthFilter — The Gatekeeper

```
Every HTTP Request
        │
        ▼
┌───────────────────────────────────────┐
│            JwtAuthFilter              │
│                                       │
│  Has "Authorization: Bearer ..."?     │
│    NO  → pass through (Spring → 401) │
│    YES ↓                              │
│                                       │
│  Is token valid (signature + expiry)? │
│    NO  → pass through (Spring → 401) │
│    YES ↓                              │
│                                       │
│  Extract email from token             │
│  Load User from DB by email           │
│  Store User in SecurityContextHolder  │
└───────────────────────────────────────┘
        │
        ▼
SecurityConfig checks role rules
        │
        ▼
Controller runs — user available via @AuthenticationPrincipal
```

---

## 12. Role-Based Access Control

| Role | Allowed Actions |
|------|----------------|
| ADMIN | Everything — upload, extract, validate, approve, reject, update findings, reports |
| REVIEWER | Approve, reject, update findings, view invoices and reports |
| VIEWER | Read-only — view invoices, findings, reports only |

How it works in code:

```java
// SecurityConfig — URL level check
.requestMatchers(HttpMethod.POST, "/api/v1/invoices/*/approve")
    .hasAnyRole("ADMIN", "REVIEWER")
// Spring checks user.getAuthorities() contains "ROLE_ADMIN" or "ROLE_REVIEWER"
// If not → 403 Forbidden
```

The `"ROLE_"` prefix is added in `User.getAuthorities()`:
```java
return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
// Role.ADMIN → "ROLE_ADMIN"
```

`hasAnyRole("ADMIN", "REVIEWER")` in SecurityConfig automatically looks for `"ROLE_ADMIN"` and `"ROLE_REVIEWER"`.
That is why the prefix must be added in `getAuthorities()`.

---

## 13. Multi-Tenancy — Org Isolation

Every invoice belongs to an org:
```
invoices.org_id → organizations.id
```

Every user belongs to an org:
```
users.org_id → organizations.id
```

Every DB query is scoped to the org:
```java
// InvoiceRepository
List<Invoice> findByOrganizationId(Long orgId);
Optional<Invoice> findByIdAndOrganizationId(Long id, Long orgId);
```

The orgId comes from the authenticated user — not from the request:
```java
public ResponseEntity<List<InvoiceResponse>> findAll(@AuthenticationPrincipal User user) {
    Long orgId = user.getOrganization().getId();
    // Company A (orgId=1) can never see Company B's (orgId=2) invoices
    // Even if they guess the invoice ID — findByIdAndOrganizationId will return empty
    return ResponseEntity.ok(service.findAll(orgId));
}
```

---

## 14. Complete Request Lifecycle

### Register (one time per company)
```
Client                          Server
  │                               │
  │  POST /auth/register          │
  │  { email, password, ... }     │
  │ ─────────────────────────────►│
  │                               │ 1. @Valid checks input
  │                               │ 2. Check email not already taken
  │                               │ 3. Save Organization to DB  (id = 1)
  │                               │ 4. Save User to DB          (BCrypt password)
  │                               │ 5. Generate JWT token
  │                               │
  │  { token, email, role, orgId }│
  │ ◄─────────────────────────────│
  │                               │
  │  (client saves the token)     │
```

### Login (when token expires or first time)
```
Client                          Server
  │                               │
  │  POST /auth/login             │
  │  { email, password }          │
  │ ─────────────────────────────►│
  │                               │ 1. authenticationManager verifies password
  │                               │ 2. Load user from DB
  │                               │ 3. Generate fresh JWT token
  │                               │
  │  { token, email, role, orgId }│
  │ ◄─────────────────────────────│
```

### Every Secured Request
```
Client          JwtAuthFilter       SecurityConfig      Controller
  │                  │                    │                  │
  │  GET /invoices   │                    │                  │
  │  Bearer token    │                    │                  │
  │ ────────────────►│                    │                  │
  │                  │ 1. Read header     │                  │
  │                  │ 2. Validate token  │                  │
  │                  │ 3. Load User from DB                  │
  │                  │ 4. Set SecurityContext                 │
  │                  │ ──────────────────►│                  │
  │                  │                    │ 5. Check role    │
  │                  │                    │ ────────────────►│
  │                  │                    │                  │ 6. @AuthenticationPrincipal
  │                  │                    │                  │ 7. Query DB with orgId
  │                  │                    │                  │
  │  [ invoices ]    │                    │                  │
  │ ◄────────────────────────────────────────────────────────│
```

---

## 15. Why Each Decision Was Made

| Decision | Reason |
|----------|--------|
| JWT over sessions | No server-side state — scales horizontally, works across services |
| BCrypt for passwords | One-way hash with random salt — even if DB is leaked, passwords are safe |
| Token expiry 24 hours | Balance between security (short-lived) and usability (not too frequent login) |
| No refresh tokens | Simpler implementation — user logs in again after 24 hours |
| STATELESS session policy | Prevents Spring from accidentally creating sessions |
| CSRF disabled | Not needed for token-based APIs — CSRF attacks require cookies |
| `OncePerRequestFilter` | Guarantees the filter runs exactly once even in complex filter chains |
| `@AuthenticationPrincipal` | Clean way to get the logged-in user — no manual SecurityContext lookup |
| `org_id` on every invoice | Hard data isolation — impossible to accidentally leak cross-tenant data |
| First user is ADMIN | Whoever registers an org owns it — they can invite other users later |
