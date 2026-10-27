# Auth Flow — Explanation Guide

---

## 1. The Core Idea

Traditional apps use **sessions** — server remembers who you are.
We use **JWT tokens** — server remembers nothing. The token carries everything.

```
Session approach  →  server stores "user 1 is logged in"
JWT approach      →  token itself says "I am user 1, ADMIN, org 1"
```

---

## 2. What is a JWT Token

A JWT is a signed string the server gives you after login.
It has 3 parts separated by dots:

```
HEADER . PAYLOAD . SIGNATURE
```

| Part | What it contains |
|------|-----------------|
| Header | which algorithm was used to sign |
| Payload | actual data — email, role, orgId, expiry |
| Signature | proves nobody tampered with the payload |

> The payload is only base64 encoded, not encrypted.
> Anyone can read it. But nobody can change it without breaking the signature.
> The server never stores the token. The client stores it and sends it with every request.

---

## 3. The 3 Flows

---

### 3.1 Register Flow

```
Client ──► AuthController ──► AuthService ──► DB (save org + user) ──► JwtService ──► Token
```

**Steps:**

1. Client sends email, password, full name, organization name
2. Input is validated — email format, password minimum 8 characters
3. Check if email is already taken — if yes, return 400 error
4. Create a new **Organization** row in the database
5. Create a new **User** row — password is BCrypt hashed before saving
6. First user of a new org is always **ADMIN**
7. Generate a JWT token with email, role, orgId inside it
8. Return the token to the client

**Things to remember:**

> **Why BCrypt?**
> One-way hash — you can never reverse it back to the original password.
> Even if the database is leaked, passwords are safe.

> **Why @Transactional?**
> If user save fails after org was already saved, the org save is also rolled back.
> No partial data left in the database.

---

### 3.2 Login Flow

```
Client ──► AuthController ──► AuthService ──► AuthenticationManager ──► JwtService ──► Token
```

**Steps:**

1. Client sends email and password
2. `AuthenticationManager` verifies the credentials
   - Loads user from DB by email
   - Runs BCrypt.matches(givenPassword, storedHash)
   - If wrong → 401 Unauthorized automatically
3. If correct → load user from DB
4. Generate a fresh JWT token
5. Return the token to the client

**Things to remember:**

> **Why load user from DB again after authenticate()?**
> `authenticate()` only tells us if credentials are valid.
> It does not return the User object. So we fetch it separately.

---

### 3.3 Secured Request Flow

```
Client ──► JwtAuthFilter ──► SecurityConfig ──► Controller ──► Service ──► DB (scoped by orgId)
```

Every request after login must carry the token:
```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

**Steps:**

1. `JwtAuthFilter` intercepts the request before it reaches the controller
2. Reads the Authorization header
3. No header or wrong format → passes through → Spring returns **401**
4. Extracts the token, validates signature and expiry
5. Invalid or expired → passes through → Spring returns **401**
6. Extracts email from inside the token
7. Loads the full User object from DB by email
8. Stores the User in `SecurityContextHolder`
9. Request moves to `SecurityConfig` rules
10. SecurityConfig checks if the URL needs a specific role
    - User has the role → continue to controller → **200**
    - User does not have the role → **403 Forbidden**
11. Controller gets the User via `@AuthenticationPrincipal`
12. Service uses `user.getOrganization().getId()` to scope all DB queries

---

## 4. Key Components

---

### 4.1 SecurityConfig
```
Purpose → The rule book of the entire security setup
```
- Defines which URLs are public and which need a token
- Defines which URLs need specific roles
- Disables sessions — every request must carry its own token
- Disables CSRF — not needed for token-based APIs
- Registers JwtAuthFilter to run before every request

---

### 4.2 JwtService
```
Purpose → The token factory — creates and reads tokens
```
- **generate** — creates a signed token with email, role, orgId inside
- **parse** — reads the claims from a token
- **extractEmail** — gets the email from inside the token
- **isValid** — checks if signature is correct and token is not expired

---

### 4.3 JwtAuthFilter
```
Purpose → The gatekeeper — runs before every single request
```
- Checks the token on every request
- Loads the user from DB
- Tells Spring who is making the request
- If no valid token → does nothing → Spring rejects the request

---

### 4.4 AuthService
```
Purpose → Business logic for register and login
```
- **register** — creates org and user, returns token
- **login** — verifies credentials, returns token

---

### 4.5 User implements UserDetails
```
Purpose → Connects our User entity to Spring Security
```
Spring Security does not know our User class.
By implementing UserDetails, we tell Spring 3 things:

| Method | What Spring uses it for |
|--------|------------------------|
| getUsername() | identify the user by email |
| getPassword() | verify BCrypt hash during login |
| getAuthorities() | check role for access control |

---

## 5. SecurityContextHolder

```
Purpose → A box that holds "who is logged in" for the duration of one request
```

```
JwtAuthFilter  ──►  puts authenticated User into the box
SecurityConfig ──►  reads from the box to check the role
Controller     ──►  reads from the box via @AuthenticationPrincipal
End of request ──►  box is cleared automatically
```

---

## 6. Role-Based Access

### Roles and Permissions

| Role | What they can do |
|------|-----------------|
| ADMIN | Everything |
| REVIEWER | Approve, reject, update findings |
| VIEWER | Read only |

### How Spring checks roles

```
User.getAuthorities()  returns  "ROLE_ADMIN"
                                      ↑
                              ROLE_ prefix is required by Spring

SecurityConfig uses  hasAnyRole("ADMIN", "REVIEWER")
                     Spring looks for "ROLE_ADMIN" and "ROLE_REVIEWER"
```

---

## 7. Multi-Tenancy — Org Isolation

```
Purpose → Company A can never see Company B's data
```

### How it works

```
Every invoice  has  org_id
Every user     has  org_id

Request comes in
    ↓
JwtAuthFilter loads User from DB
    ↓
Controller gets User via @AuthenticationPrincipal
    ↓
All DB queries filter by user.getOrganization().getId()
    ↓
Company A (orgId=1) → only sees invoices where org_id = 1
Company B (orgId=2) → only sees invoices where org_id = 2
```

> Even if Company A guesses an invoice ID of Company B,
> the query checks both `id` AND `org_id` — so it returns empty.

---

## 8. Common Questions

---

**Q: Why is the token not encrypted?**

The payload contains email, role, orgId — not sensitive secrets.
What matters is that nobody can forge or tamper with it.
The signature handles that.

---

**Q: What happens when the token expires?**

`isValid()` returns false → JwtAuthFilter skips setting auth → Spring returns 401.
Client must login again to get a new token.

---

**Q: Why no refresh tokens?**

Simpler implementation. User logs in again after 24 hours.
Refresh tokens add complexity — a separate endpoint, a separate token store.

---

**Q: Why CSRF disabled?**

CSRF attacks trick a browser into sending a cookie automatically.
We use tokens in the Authorization header — browsers never send those automatically.
So CSRF is not a threat here.

---

**Q: Why STATELESS session?**

Prevents Spring from creating a server-side session.
With JWT, the server should remember nothing between requests.
Every request is self-contained.

---

**Q: Why is the first registered user always ADMIN?**

Whoever creates the organization owns it.
They can then add other users with REVIEWER or VIEWER roles.
