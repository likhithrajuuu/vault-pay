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

The identity service runs on port 8080 by default. Set `SERVER_PORT` to override it. Spring Security's default security remains enabled; this scaffold has no public application endpoints or custom authentication flows.

## Identity service

[backend/identity-service](backend/identity-service) is a plain Spring Boot application with a context-load smoke test and these dependency groups:

| Dependency | Purpose |
| --- | --- |
| Spring Web MVC | Servlet HTTP/REST foundation |
| Validation | Request bean validation |
| Actuator | Operational health and metrics foundation |
| Spring Security | Authentication and authorization foundation |
| OAuth2 Client | OAuth2/OIDC integration foundation for Google and Apple |
| OAuth2 Resource Server | Bearer-token authentication foundation, including JWT/JOSE signing and verification APIs |
| Spring Boot and Security test starters | JUnit, application context, HTTP, and security testing support |

JWT/JOSE support is supplied transitively through Spring Security; a separate JWT library or provider-specific Google/Apple SDK is not required for this foundation. Dependency versions follow Spring Boot's dependency management.

OAuth registrations and JWT validation/issuance are intentionally not enabled yet. The application can start without provider credentials. Before implementing sign-in:

- Google requires an OAuth client registration, credentials, and registered redirect URI.
- Apple requires a Services ID, associated app configuration, signing key/key ID and team ID, a signed client-secret JWT, and registered return URLs. Apple's callback behavior must be handled explicitly when implementing the flow.
- VaultPay token issuance will need its own issuer, signing keys, claims, expiry, and validation policy; provider ID tokens do not automatically become VaultPay API access tokens.

Keep credentials and signing keys outside source control. gRPC, Kafka, persistence, and business authentication endpoints are deferred to later implementation decisions.

References: [Spring Security JWT support](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html), [Spring Boot OAuth2 configuration](https://docs.spring.io/spring-boot/reference/security/oauth2.html), and [Apple environment setup](https://developer.apple.com/documentation/signinwithapple/configuring-your-environment-for-sign-in-with-apple).

## Design documents

- [System design proposal](docs/HLD.md)
- [Requirements and capacity assumptions](docs/REQUIREMENTS.md)
- [Low-level design](docs/LLD.md)
