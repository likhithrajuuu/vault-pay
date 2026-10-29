# VaultPay - Your secure payments simulation
# VaultPay

Distributed wallet and payment processing platform using simulated funds.

## Backend build

All backend modules use **Java 21**, **Spring Boot 4.1.2**, and **Gradle Kotlin DSL**. Kotlin is used for build scripts; application code is Java. Use the root Gradle wrapper (9.8.2); new backend services should join this build rather than introduce Maven or separate wrappers.

Prerequisite: install JDK 21 and set `JAVA_HOME` to it. The first build requires access to Gradle distributions and Maven Central.

```sh
./gradlew build
./gradlew :backend:identity-service:bootRun
```

The identity service runs on port 8080 by default. Set `SERVER_PORT` to override it. Configure the external keys and PostgreSQL connection described below before running it. Builds/tests do not need a database.

## Identity service

[backend/identity-service](backend/identity-service) implements local email/password authentication and stateless bearer-token authorization with these dependencies:

| Dependency | Purpose |
| --- | --- |
| Spring Web MVC | Servlet HTTP/REST foundation |
| Validation | Request bean validation |
| Actuator | Operational health and metrics foundation |
| Spring Security | Authentication and authorization foundation |
| OAuth2 Client | OAuth2/OIDC integration foundation for Google and Apple |
| OAuth2 Resource Server | Bearer-token authentication foundation, including JWT/JOSE signing and verification APIs |
| JDBC / PostgreSQL | Parameterized lookup of local credentials in the Identity-owned schema |
| Bouncy Castle | Argon2id password hashing implementation used by Spring Security |
| Spring Boot and Security test starters | JUnit, application context, HTTP, and security testing support |

JWT/JOSE support is supplied transitively through Spring Security; a separate JWT library or provider-specific Google/Apple SDK is not required. Dependency versions follow Spring Boot's dependency management except Bouncy Castle, which is explicitly pinned.

### Local API authentication

| Endpoint | Access and behavior |
| --- | --- |
| `POST /api/v1/auth/login` | Public JSON login with `email` and `password`; checks an existing active local account and returns `accessToken`, `tokenType`, and `expiresIn` |
| `GET /api/v1/identity/me` | Requires a valid bearer JWT with `identity:read`; returns the authenticated UUID and granted scopes |
| `GET /.well-known/jwks.json` | Public RSA verification parameters only |
| `GET /actuator/health` | Public health status |
| Other paths/methods | Denied by default; new endpoints need explicit authorization rules |

Login uses Argon2id with a random 16-byte salt, 32-byte output, 19 MiB memory, two iterations, and parallelism one. Hashes use the bare PHC format required by the migration. Only `ACTIVE` accounts with a local credential row may log in. Unknown, OAuth-only, pending, suspended, and closed accounts receive the same generic credential error. No user registration, activation endpoint, seeded credentials, or password-reset workflow is added by this change.

Tokens are RS256-signed and expire after 15 minutes. Validation pins RS256 and checks issuer, audience, canonical UUID subject, timestamps, token ID, and maximum lifetime. Spring's timestamp validation allows a 60-second clock skew. Scopes are server-controlled; local login grants only `identity:read`. Both request rules and method security protect the identity endpoint. Basic authentication, form login, sessions, and cookie authentication are disabled. CSRF is disabled for this header-only bearer API; a future browser OAuth flow requires separate state/CSRF handling.

All API errors use `application/problem+json` with `type`, `title`, `status`, `detail`, `instance`, `code`, `timestamp`, and `fieldErrors`. Validation errors never include rejected values. Authentication errors never expose account status, SQL errors, token claims, or exception messages.

| Status | Example code | Meaning |
| --- | --- | --- |
| 400 | `VALIDATION_FAILED`, `INVALID_JSON`, `INVALID_AUTHORIZATION_HEADER` | Invalid request |
| 401 | `INVALID_CREDENTIALS`, `AUTHENTICATION_REQUIRED`, `INVALID_TOKEN` | Login failed or bearer token missing/invalid/expired |
| 403 | `INSUFFICIENT_PERMISSIONS` | Authenticated caller is not allowed to perform the operation |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | Login body must use a supported JSON content type |
| 503 | `AUTHENTICATION_UNAVAILABLE` | Credential storage is unavailable or its schema is missing |
| 500 | `INTERNAL_ERROR` | Unexpected failure; implementation details stay server-side |

### External configuration

Set these environment variables in the process or secret manager; `.env` files are not automatically loaded by Spring Boot:

```text
IDENTITY_DB_URL=jdbc:postgresql://localhost:5432/vaultpay_identity
IDENTITY_DB_USERNAME=<database-role>
IDENTITY_DB_PASSWORD=<database-password>
JWT_PRIVATE_KEY_LOCATION=file:/absolute/path/to/private-key.pem
JWT_PUBLIC_KEY_LOCATION=file:/absolute/path/to/public-key.pem
JWT_KEY_ID=<stable-key-id>
JWT_ISSUER=<identity-service-issuer-url>
JWT_AUDIENCE=vaultpay-identity
```

Provide a matching RSA pair of at least 2048 bits: PKCS#8 private PEM and X.509 public PEM. Startup fails if keys are missing, weak, or mismatched. Keys are never generated automatically for a deployment, stored in user rows, or committed to the repository. The current implementation loads one key pair at startup and publishes one public key. Overlapping-key rotation is a future extension; replacing the key immediately invalidates tokens signed by the previous key.

Flyway remains disabled and SQL initialization is set to `never`. The migration must be applied separately by an authorized operational step before local login can read accounts. This implementation does not run migrations or seed users. The authentication tests exclude DataSource auto-configuration, mock credential storage, and generate temporary test-only RSA keys; they use real Argon2 and signed JWTs without contacting PostgreSQL.

Account status is checked at login. Already issued bearer tokens remain valid until expiry (plus clock skew); immediate revocation, refresh/logout, login rate limiting, and brute-force controls require further implementation or gateway policy. Configure HTTPS and edge abuse controls before exposing the login endpoint publicly. Cookie/browser OAuth integration is not enabled by this API chain.

### Google / Apple follow-up

Provider registrations and browser sign-in remain unconfigured:

- Google requires an OAuth client registration, credentials, and registered redirect URI.
- Apple requires a Services ID, associated app configuration, signing key/key ID and team ID, a signed client-secret JWT, and registered return URLs. Apple's callback behavior must be handled explicitly when implementing the flow.
- Provider ID tokens do not automatically become VaultPay API access tokens; verified identities need an explicit mapping/linking flow.

Keep credentials and signing keys outside source control. gRPC and Kafka remain deferred.

References: [Spring Security JWT support](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html), [Spring Boot OAuth2 configuration](https://docs.spring.io/spring-boot/reference/security/oauth2.html), and [Apple environment setup](https://developer.apple.com/documentation/signinwithapple/configuring-your-environment-for-sign-in-with-apple).

## Design documents

- [System design proposal](docs/HLD.md)
- [Requirements and capacity assumptions](docs/REQUIREMENTS.md)
- [Low-level design](docs/LLD.md)
