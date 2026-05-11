# Broker Platform Security Audit

**Audited project:** broker-platform-core / core
**Audit date:** 2026-05-11
**Auditor:** Claude (automated static review)
**Modules in scope:**
- `account-service` — money account management
- `common` — shared models, DTOs, validators, exceptions
- `holdings-service` — stock and options holdings
- `trade-service` — order placement and cancellation
- `transaction-service` — trade transaction records
- `user-gateway` — Spring Cloud Gateway, JWT validation
- `user-service` — user registration, authentication, password management
- `exchange-client-service` — exchange WebSocket listener and order update dispatcher
- `notification-service` — WebSocket push to end-users (present in VCS)
- `bot-gateway` — bot-facing gateway (present in VCS)
- `docker-compose.yml`, all `application.properties` / `application.yml`, `Dockerfile`s, `.env.example`

**Out of scope:**
- `build/`, `target/`, generated code
- Test fixtures
- `.env` (gitignored; not tracked in VCS — confirmed via `git log -- .env`)

---

## Executive summary

19 findings were identified: 2 Critical, 6 High, 5 Medium, 5 Low, and 1 Informational. The most severe issue is that four internal financial-mutation endpoints (`/funds/freeze`, `/funds/unfreeze`, `/funds/deduct/frozen`, `/funds/deduct`) accept the target `userId` from the JSON request body rather than from the gateway-injected `X-User-Id` header, allowing any authenticated trader to drain another trader's balance or destroy the freeze mechanism. A second critical issue is that the gateway appends (rather than replaces) the `X-User-Id` and `X-Username` headers, meaning a client who includes a spoofed `X-User-Id` in their request will have that value honoured by all downstream services. Together these two flaws allow any registered user to steal funds from or fully impersonate every other account in the platform. Additional High findings cover IDOR on holdings, orders, and transactions, credential leakage through application logs, and unauthenticated access to all internal services due to every service port being published in the Docker Compose file. The overall security posture is insufficient for a regulated retail broker context.

---

## Severity summary

| Severity | Count |
|----------|-------|
| Critical | 2     |
| High     | 6     |
| Medium   | 5     |
| Low      | 5     |
| Info     | 1     |

---

## Findings

### BR-001 — Internal fund mutation endpoints accept `userId` from request body [Critical]

**Module:** account-service
**Location:** `account-service/src/main/java/lynx/team2/controller/AccountController.java:70–91`
**Category:** Authorization

**Description.** Four endpoints that mutate account balances — `POST /funds/freeze`, `POST /funds/unfreeze`, `POST /funds/deduct/frozen`, and `POST /funds/deduct` — read the target `userId` from the JSON request body (`FundsOperationRequest.userId()`). The user-gateway routes `/funds/**` through JWT validation and injects `X-User-Id` from the authenticated token, but these four endpoints never read `X-User-Id`; they operate on whichever `userId` appears in the body. No binding between the caller's identity and the body field is enforced at any layer.

**Impact.** Any authenticated trader can send `POST /funds/deduct` with an arbitrary `userId` and drain that user's entire available balance, or send `POST /funds/freeze` to lock another user's funds ahead of a legitimate order. Fund theft is achievable at scale by any registered user with a valid session token.

**Evidence.**
```java
// AccountController.java:70–91
@PostMapping("/funds/freeze")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void freezeFunds(@RequestBody FundsOperationRequest request) {
    accountService.freezeFunds(request.userId(), request.currency(), request.amount());
}

@PostMapping("/funds/deduct")
public void deductFunds(@RequestBody FundsOperationRequest request) {
    accountService.deductFunds(request.userId(), request.currency(), request.amount());
}
```

**Recommendation.** Remove `userId` from `FundsOperationRequest` for all user-reachable paths. Derive the target account exclusively from the gateway-injected `@RequestHeader("X-User-Id") Long userId` parameter. For the internal service-to-service calls (freeze on BUY, deduct on fill), enforce a separate internal authentication mechanism (e.g., a shared secret header or a dedicated internal network route that is not exposed through the public gateway).

---

### BR-002 — Gateway appends X-User-Id header instead of replacing it [Critical]

**Module:** user-gateway
**Location:** `user-gateway/src/main/java/lynx/team2/filter/JwtAuthenticationFilter.java:78–81`
**Category:** Authentication

**Description.** `ServerHttpRequest.mutate().header(name, value)` in Spring WebFlux appends to the existing header list rather than replacing it. If a client includes `X-User-Id: 999` in their HTTP request alongside a valid JWT for user 1, the gateway adds a second `X-User-Id: 1` but preserves the client-supplied value. The HTTP spec and Spring's `@RequestHeader` resolver return the first value for a single-valued header binding, which is the client-supplied spoofed identity. Every downstream service that does `@RequestHeader("X-User-Id") Long userId` will use the attacker-controlled value.

**Impact.** Any holder of a valid JWT can set `X-User-Id` to any other user's ID and fully impersonate that user across all services: view balances, place and cancel orders, retrieve transaction history, modify holdings, and change usernames. This is a complete authentication bypass for the entire internal service mesh.

**Evidence.**
```java
// JwtAuthenticationFilter.java:78–81
ServerHttpRequest mutated = request.mutate()
        .header("X-User-Id", userId == null ? "" : userId)
        .header("X-Username", username == null ? "" : username)
        .build();
```

**Recommendation.** Explicitly strip the incoming headers before setting them:
```java
ServerHttpRequest mutated = request.mutate()
        .headers(h -> { h.remove("X-User-Id"); h.remove("X-Username"); })
        .header("X-User-Id", userId == null ? "" : userId)
        .header("X-Username", username == null ? "" : username)
        .build();
```
Apply the same fix to `bot-gateway/src/main/java/lynx/team2/filter/BotAuthenticationFilter.java:72–74`.

---

### BR-003 — Exchange credentials logged in plaintext via WebSocket URL [High]

**Module:** exchange-client-service
**Location:** `exchange-client-service/src/main/java/lynx/team2/client/ExchangeWebSocketClient.java:58–59`
**Category:** Secrets / Logging

**Description.** The WebSocket URL is constructed by concatenating the API key and secret as URL query parameters, and the full URL is then passed to `log.info()`. This means every connection attempt (including reconnects, which fire every 5 seconds on disconnect) writes the exchange credentials to the application log at INFO level.

**Impact.** Any system or person with read access to application logs — log aggregators, SIEM, cloud log storage, developers with `kubectl logs` access — can retrieve the exchange API credentials. With those credentials an attacker can place orders, cancel orders, and view the full order book under the broker's identity on the exchange, leading to financial loss and regulatory liability.

**Evidence.**
```java
// ExchangeWebSocketClient.java:58–59
URI uri = URI.create(wsUrl + "?api_key=" + apiKey + "&api_secret=" + apiSecret);
log.info("Connecting to exchange WebSocket: {}", uri);
```

**Recommendation.** Remove credentials from the URL. If the exchange supports header-based auth on the WebSocket upgrade (`Authorization: Bearer …`), use that. If it requires query parameters, log a redacted URI: `log.info("Connecting to exchange WebSocket: {}", wsUrl)` (omit parameters). Additionally, rotate the exchange credentials immediately because they have already been written to any logs produced in development.

---

### BR-004 — Holdings update and deletion have no ownership check [High]

**Module:** holdings-service
**Location:** `holdings-service/src/main/java/lynx/team2/controller/HoldingsController.java:66–84`
**Category:** Authorization

**Description.** `PUT /holdings/{id}` fetches the holding by the path ID without verifying that `holding.getUser().getUserId()` matches the caller's `X-User-Id`. `DELETE /holdings/{id}` deletes by path ID with no ownership check at all.

**Impact.** Any authenticated trader can enumerate holding IDs (which are sequential Long PKs) and zero out or delete another trader's equity positions. This permanently destroys portfolio records and could be used to manipulate account value before a forced liquidation or regulatory audit.

**Evidence.**
```java
// HoldingsController.java:66–84
@PutMapping("/holdings/{id}")
public HoldingResponse updateHolding(@PathVariable Long id,
                                     @RequestBody UpdateHoldingRequest request) {
    Holding existing = holdingsRepository.findById(id)
            .orElseThrow(() -> new RepoException("Holding not found: " + id));
    existing.setAmount(request.amount());        // no ownership check
    existing.setAverageCost(request.averageCost());
    return toResponse(holdingsService.updateHolding(existing));
}

@DeleteMapping("/holdings/{id}")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void deleteHolding(@PathVariable Long id) {
    holdingsService.deleteHolding(id);           // no ownership check
}
```

**Recommendation.** After fetching the holding, assert `existing.getUser().getUserId().equals(userId)` (where `userId` is from `@RequestHeader("X-User-Id")`). Return 403 Forbidden if the IDs do not match.

---

### BR-005 — Order cancellation and retrieval expose any trader's orders [High]

**Module:** trade-service
**Location:** `trade-service/src/main/java/lynx/team2/controller/TradeController.java:27–35`
**Category:** Authorization

**Description.** `GET /orders/{orderId}` and `DELETE /orders/{orderId}` proxy the request directly to the exchange without verifying that the order belongs to the authenticated user. The exchange returns order data including `platform_user_id`, which is the broker's internal user ID, but this field is never compared against the caller.

**Impact.** Any authenticated trader can cancel another trader's open order (causing financial loss on time-sensitive trades) and can retrieve the full order details of any order on the platform, including instrument, quantity, price, and status.

**Evidence.**
```java
// TradeController.java:27–35
@GetMapping("/{orderId}")
public ExchangeClient.ExchangeOrder getOrder(@PathVariable String orderId) {
    return tradeService.getOrder(orderId);   // no ownership check
}

@DeleteMapping("/{orderId}")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void cancelOrder(@PathVariable String orderId) {
    tradeService.cancelOrder(orderId);       // no ownership check
}
```

**Recommendation.** In `TradeService.getOrder()` and `cancelOrder()`, fetch the order from the exchange first, compare `exchangeOrder.platformUserId()` against the `userId` parameter from the JWT, and throw a 403 if they do not match.

---

### BR-006 — Transaction deletion has no ownership check [High]

**Module:** transaction-service
**Location:** `transaction-service/src/main/java/lynx/team2/controller/TransactionController.java:68–72`
**Category:** Authorization

**Description.** `DELETE /transactions/{id}` deletes the transaction record identified by path ID without verifying that `transaction.getUser().getUserId()` equals the authenticated caller's ID.

**Impact.** Any authenticated trader can delete any other trader's transaction records, destroying the audit trail that regulators require to be retained. This could be used to cover up illicit trading activity or to harass another user by erasing their history.

**Evidence.**
```java
// TransactionController.java:68–72
@DeleteMapping("/{id}")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void deleteTransaction(@PathVariable Long id) {
    transactionService.deleteTransaction(id);   // no ownership check
}
```

**Recommendation.** Before deletion, fetch the transaction, verify ownership against `@RequestHeader("X-User-Id")`, and return 403 on mismatch. Additionally, consider whether unauthenticated users of the internal `POST /transactions` endpoint should be able to create records at all (see BR-001 discussion).

---

### BR-007 — Username change uses path `userId` without authorization check [High]

**Module:** user-service
**Location:** `user-service/src/main/java/lynx/team2/controller/AuthController.java:104–119`
**Category:** Authorization

**Description.** `PUT /users/{userId}/username` accepts the target user ID from the URL path and does not verify that it matches the caller's authenticated ID (available via `@RequestHeader("X-User-Id")`). `PUT /users/{userId}/password` has the same structural flaw, though it is partially mitigated by requiring the old password.

**Impact.** Any authenticated trader can rename any other trader's username. This locks the victim out of their account if they rely on their username for identification, causes confusion, and may allow an attacker to take a desirable username for social-engineering purposes. The `/password` variant is lower severity in practice (requires knowing the victim's current password) but shares the same design defect.

**Evidence.**
```java
// AuthController.java:104–119
@PutMapping("/{userId}/username")
public AuthResponse changeUsername(
        @PathVariable Long userId,              // attacker-controlled
        @RequestBody ChangeUsernameRequest request
) {
    User user = userService.changeUsername(userId, request.newUsername());
    ...
}
```

**Recommendation.** Remove `userId` from the path entirely. Derive it solely from the gateway-injected `@RequestHeader("X-User-Id") Long userId`. The authenticated user should only be able to modify their own profile.

---

### BR-008 — All internal service ports published publicly in Docker Compose [High]

**Module:** Deployment
**Location:** `docker-compose.yml:26–27, 46–47, 62–63, 79–80, 93–94, 121–122, 151–152`
**Category:** Configuration

**Description.** Every internal microservice has its port published to the host: user-service 8082, account-service 8083, transaction-service 8084, holdings-service 8085, trade-service 8086, exchange-client-service 8087, notification-service 8088. The `SecurityConfig` comment (`SecurityConfig.java:17–20`) explicitly warns that internal ports "must NOT be exposed publicly" and relies on network-layer isolation — but the Compose file provides no such isolation and binds all ports to `0.0.0.0`.

**Impact.** An attacker who can reach the Docker host (e.g., on a shared cloud instance or via SSRF) can call any internal endpoint directly, bypassing the gateway's JWT validation entirely. Combined with BR-001, this means fund theft without any authentication.

**Evidence.**
```yaml
# docker-compose.yml:46–47
account-service:
  ports:
    - "8083:8083"   # internal fund-mutation endpoints exposed
```

**Recommendation.** Remove `ports:` from all internal services (account, transaction, holdings, trade, exchange-client, notification). Define a private Docker network and attach only the gateways (user-gateway on 8080, bot-gateway on 8090) to an external-facing network. Internal services communicate exclusively over the private network. Example:
```yaml
networks:
  internal:
  public:
    driver: bridge
services:
  account-service:
    networks: [internal]   # no ports published
  user-gateway:
    networks: [internal, public]
    ports: ["8080:8080"]
```

---

### BR-009 — Email enumeration via forgot-password endpoint [Medium]

**Module:** user-service
**Location:** `user-service/src/main/java/lynx/team2/service/UserService.java:126–128`, `account-service/src/main/java/lynx/team2/exception/GlobalExceptionHandler.java:27–31`
**Category:** Authentication

**Description.** `createPasswordResetToken()` throws `IllegalArgumentException("User not found")` when the submitted email does not belong to any account. This propagates to the generic exception handler, which returns HTTP 500 with body `{"error": "Internal server error: User not found"}`. A request for a registered email returns HTTP 200. The two responses are structurally different, allowing attackers to enumerate which email addresses are registered on the platform.

**Impact.** Attackers can compile a list of registered trader emails and target them with phishing, credential stuffing, or SIM-swap attacks.

**Evidence.**
```java
// UserService.java:126–128
public User createPasswordResetToken(String email) {
    User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
```

**Recommendation.** Return the same HTTP 200 response (`{"message": "If that address is registered, a reset email has been sent"}`) regardless of whether the email exists. Silently skip the email send if the user is not found.

---

### BR-010 — Email verification not enforced at login [Medium]

**Module:** user-service
**Location:** `user-service/src/main/java/lynx/team2/service/UserService.java:71–79`
**Category:** Authentication

**Description.** The `login()` method validates the password but does not check `user.isEmailVerified()`. A user who registers with a fake or typo'd email address can immediately log in, place orders, and access all trading functions.

**Impact.** The broker cannot reliably contact users about order fills, margin calls, or regulatory notices. An attacker can register with a victim's email and immediately start trading under the victim's email identity before the victim notices. The email-verification mechanism provides no security guarantee.

**Evidence.**
```java
// UserService.java:71–79
public User login(String email, String rawPassword) {
    User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new IllegalArgumentException("Invalid email"));
    if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
        throw new IllegalArgumentException("Invalid password");
    }
    return user;   // emailVerified never checked
}
```

**Recommendation.** Add `if (!user.isEmailVerified()) throw new IllegalStateException("Email not verified")` before returning the user. Return HTTP 403 with a message prompting the user to check their inbox.

---

### BR-011 — No rate limiting on authentication or order endpoints [Medium]

**Module:** user-gateway, user-service
**Location:** `user-gateway/src/main/java/lynx/team2/filter/JwtAuthenticationFilter.java` (no limiter), `user-service/src/main/java/lynx/team2/controller/AuthController.java` (no limiter)
**Category:** DoS / Authentication

**Description.** No rate limiting or account lockout is implemented on login, registration, password reset, or order placement. The gateway filter (JwtAuthenticationFilter) performs only JWT validation with no request-rate tracking.

**Impact.** Credential stuffing attacks against `/users/login` are unconstrained. An attacker can try millions of passwords per hour against a target account. Password reset can be abused to spam victims with emails and exhaust SMTP quota. Order placement can be flooded to overload the exchange connector.

**Recommendation.** Apply a request-rate limiter in the Spring Cloud Gateway using `RequestRateLimiter` with Redis (built-in to Spring Cloud Gateway) on at minimum `/users/login`, `/users/forgot-password`, and `/orders`. Consider account lockout (e.g., 10 failed logins → 15-minute lockout stored in Redis) in `UserService.login()`.

---

### BR-012 — Unauthenticated notification endpoints allow message injection [Medium]

**Module:** notification-service
**Location:** `notification-service/src/main/java/lynx/team2/controller/NotificationController.java:22–36`
**Category:** Authorization

**Description.** `POST /notify/{userId}` and `POST /broadcast` are completely unauthenticated (the service's `SecurityConfig` permits all requests). Any party reachable to port 8088 — including any internal service or, if port 8088 is exposed per BR-008, any external actor — can push arbitrary `NotificationMessage` payloads to any user's WebSocket connection or to all connected users simultaneously.

**Impact.** An attacker can broadcast fake `ORDER_UPDATE` messages claiming orders filled at favourable prices, inducing users to take further market positions based on false information. This constitutes market manipulation. They can also send social-engineering messages that impersonate the broker.

**Evidence.**
```java
// NotificationController.java:32–36
@PostMapping("/broadcast")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void broadcast(@RequestBody NotificationMessage message) {
    sessionRegistry.broadcast(message);   // no auth check
}
```

**Recommendation.** Restrict the notification endpoints to calls originating from exchange-client-service using a shared internal secret header (e.g., `X-Internal-Token`) validated at the filter level, and ensure this port is not reachable from outside the Docker network (see BR-008).

---

### BR-013 — Order fill settlement not implemented; frozen funds permanently locked [Medium]

**Module:** exchange-client-service
**Location:** `exchange-client-service/src/main/java/lynx/team2/service/OrderUpdateProcessor.java:38–44`
**Category:** Other (financial integrity)

**Description.** When the exchange sends an `ORDER_UPDATE` event with status `FILLED` or `PARTIALLY_FILLED`, `OrderUpdateProcessor.process()` only forwards the notification — it does not deduct the frozen funds, does not update the transaction status, and does not credit the holding. The block comment explicitly marks this as a TODO. As a result, every successfully filled BUY order permanently locks the estimated cost in `frozenBalance` with no automated path to settlement.

**Impact.** Users' available balances are permanently reduced after any filled BUY order. Over time, all liquidity is trapped in `frozenBalance`, rendering accounts unusable without manual database intervention. This is a denial-of-service against every trading user.

**Evidence.**
```java
// OrderUpdateProcessor.java:38–44
// TODO: on FILLED / PARTIALLY_FILLED:
//   1. Look up our Transaction by exchangeOrderId
//   2. Update transaction.status, filled_quantity, exchange_fee
//   3. Call accountService.deductFrozenFunds for the cash settled
//   4. Call holdingsService to upsert the position with new average_cost
```

**Recommendation.** Implement the TODO. Expose `TransactionService.findByExchangeOrderId()` as an HTTP endpoint, call it from `OrderUpdateProcessor`, deduct the actual fill cost from `frozenBalance`, release the remainder back to `balance`, and upsert the holding with the new quantity and average cost.

---

### BR-014 — Exchange API credentials hardcoded in committed configuration files [Low]

**Module:** trade-service, exchange-client-service
**Location:** `trade-service/src/main/resources/application.properties:18–19`, `exchange-client-service/src/main/resources/application.properties:13–14`, `trade-service/src/main/resources/application.properties:5–6`
**Category:** Secrets

**Description.** Exchange API key/secret (`dev-key` / `dev-secret`) and database credentials (`lynxteam2` / `lynxteam2`) are hardcoded in `application.properties` files that are tracked in VCS. The docker-compose file uses env-var substitution with these dev values as the fallback default (`${EXCHANGE_API_KEY:-dev-key}`), meaning any deployment that does not set these variables will silently use the insecure defaults.

**Impact.** Any developer or CI system with repo access sees the exchange credentials. The git history will retain these values even after they are changed unless an explicit history rewrite is performed. The silent fallback to `dev-key` means a misconfigured production deployment would authenticate to the exchange with a known credential.

**Evidence.**
```properties
# trade-service/application.properties:18–19
exchange.api-key=dev-key
exchange.api-secret=dev-secret
```

**Recommendation.** Replace all credential literals in `application.properties` with `${ENV_VAR}` (no default) so the service fails to start rather than using a known insecure value. Move these into the `.env` file (already gitignored) or a secrets manager.

---

### BR-015 — Verbose error responses expose internal exception messages [Low]

**Module:** account-service, holdings-service, transaction-service, trade-service
**Location:** `account-service/src/main/java/lynx/team2/exception/GlobalExceptionHandler.java:27–31`
**Category:** Configuration

**Description.** The catch-all `Exception` handler returns `"Internal server error: " + e.getMessage()` in the HTTP response body. JPA exceptions, repository lookup failures, and other runtime errors include table names, column names, and SQL fragments in their messages.

**Impact.** Attackers can probe endpoints with malformed input to extract database schema information, which materially accelerates SQL injection reconnaissance (even though Spring Data JPA parameterises queries by default).

**Evidence.**
```java
// GlobalExceptionHandler.java:27–31
@ExceptionHandler(Exception.class)
public ResponseEntity<Map<String, String>> handleException(Exception e) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("error", "Internal server error: " + e.getMessage()));
}
```

**Recommendation.** Return a generic `"An unexpected error occurred"` string in production. Log the full exception server-side with a correlation ID, and return that ID so support staff can look up details without exposing them to clients.

---

### BR-016 — No HTTP security headers configured [Low]

**Module:** user-gateway
**Location:** `user-gateway/src/main/java/lynx/team2/config/CorsConfig.java`, `user-gateway/src/main/java/lynx/team2/filter/JwtAuthenticationFilter.java`
**Category:** Configuration

**Description.** The gateway does not add any of the standard defensive HTTP response headers: `Content-Security-Policy`, `Strict-Transport-Security`, `X-Content-Type-Options`, `X-Frame-Options` / `frame-ancestors`, or `Referrer-Policy`. The `Authorization` header is listed in `Expose-Headers`, which is unnecessary for a Bearer-token API and may assist token-harvesting scripts.

**Impact.** If a frontend is served from the same origin, the absence of CSP and clickjacking protection increases the exploitability of any XSS or UI-redress vulnerability. Without HSTS, a man-in-the-middle can downgrade connections to HTTP.

**Recommendation.** Add a `WebFilter` in the gateway that appends these headers to every response:
```
Strict-Transport-Security: max-age=63072000; includeSubDomains
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
Referrer-Policy: no-referrer
Content-Security-Policy: default-src 'self'
```
Remove `Authorization` from `exposedHeaders` in `CorsConfig`.

---

### BR-017 — SQL queries logged at INFO level in user-service [Low]

**Module:** user-service
**Location:** `user-service/src/main/resources/application.properties:9`
**Category:** Logging

**Description.** `spring.jpa.show-sql=true` causes Hibernate to write every SQL statement — including those containing email addresses in `WHERE` clauses — to the application log.

**Impact.** Logs are often stored in centralised systems with broader access than the application itself. User PII (emails) appears in plaintext across log entries for every login, password reset, and registration.

**Recommendation.** Set `spring.jpa.show-sql=false` in all environments. If query logging is required for debugging, enable it only locally using a dev-only profile and never commit the setting as `true`.

---

### BR-018 — Hibernate DDL auto-update enabled in all services [Low]

**Module:** All services
**Location:** All `application.properties` files, `spring.jpa.hibernate.ddl-auto=update`
**Category:** Configuration

**Description.** `ddl-auto=update` instructs Hibernate to alter the production database schema on every application startup to match the current entity model. Hibernate's update mode can add columns and tables but cannot safely handle column renames, type changes, or constraint modifications, and may produce silent data loss or corruption.

**Impact.** A code change that renames an entity field can cause Hibernate to add a new column (with null values) while leaving the old column orphaned, silently losing all previously stored data in that column on next restart.

**Recommendation.** Set `spring.jpa.hibernate.ddl-auto=validate` in production. Manage schema changes with a migration tool (Liquibase or Flyway) that applies versioned, reviewed scripts under source control.

---

### BR-019 — No password strength policy on registration [Info]

**Module:** user-service
**Location:** `user-service/src/main/java/lynx/team2/controller/AuthController.java:31–51`
**Category:** Authentication

**Description.** `RegisterRequest` and `UserService.signUp()` apply no minimum length, character-class, or entropy requirements to the submitted password. A user can register with password `a`.

**Recommendation.** Enforce a minimum of 12 characters and at least one character from two distinct classes (letter + digit + symbol). Reject passwords that appear in common breach lists (e.g., via the HaveIBeenPwned API or a local blocklist).

---

## Out-of-scope observations

- `trade-service/src/main/java/lynx/team2/service/TradeService.java:111–125`: For MARKET orders, if the exchange price fetch fails, `estimateCost` silently returns `null` and the funds freeze is skipped entirely. The order is still submitted. If the exchange fills it, no funds are available to settle — this will produce a deficit that `deductFrozenFunds` may absorb from the regular balance without warning.
- `account-service/src/main/java/lynx/team2/service/AccountServiceImpl.java:87`: `deductFunds` rejects a withdrawal that would bring the balance to **exactly zero** (`<= 0` comparison). A user cannot spend their last cent.
- `user-service/src/main/java/lynx/team2/service/UserService.java:32–39`: `signUp()` distinguishes between "Email already exists" and "Username already exists" error messages, leaking whether an email or username is registered. Apply the same fix as BR-009 (a generic "registration failed" message).
- `bot-gateway/src/main/java/lynx/team2/filter/BotAuthenticationFilter.java:72–74`: Same header-append vulnerability as BR-002 for `X-Bot-Id`.
- The `JwtHandshakeInterceptor` in `notification-service` accepts the JWT as a URL query parameter (`?token=`). While necessary for WebSocket clients that cannot set headers, tokens in URLs are captured in server access logs, browser history, and referrer headers. Consider limiting token lifetime or using a short-lived one-time handshake token.

---

## Methodology

All seven declared in-scope modules were reviewed, plus `exchange-client-service`, `notification-service`, and `bot-gateway` which were found to contain substantive code. Files reviewed included all `.java` source files under `src/main/java`, all `application.properties` and `application.yml` configuration files, `docker-compose.yml`, `.env.example`, and the root `.gitignore`. The `.env` file was confirmed not tracked in git via `git log -- .env` (no output). Build artifacts under `build/` and `target/` were excluded.

No dynamic testing, fuzzing, or exploit execution was performed. No external systems were contacted. All findings are grounded in static code reading with exact `file:line` references. Dependency manifests (`build.gradle`) were reviewed for library versions; a full `gradle dependencyCheckAnalyze` or equivalent was not run and is recommended as a follow-up.