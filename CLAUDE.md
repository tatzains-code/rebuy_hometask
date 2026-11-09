# CLAUDE.md

Guidance for AI coding assistants working in this repository.

## Project

URL shortener MVP for a take-home challenge (rebuy). A user submits a long URL via a web form or REST API and receives a short URL with a **4-character code**; opening the short URL redirects (`302`) to the original.

Time budget is 3–4 hours total, so prefer the simplest correct solution. Do not add features, abstractions or dependencies that are not listed in `FEATURES.md`.

## How to work in this repo

- Work is split into features F0–F10 in `FEATURES.md`. Implement **one feature at a time, in order**, unless told otherwise.
- Before starting a feature, read its section in `FEATURES.md` completely. Its acceptance criteria are the definition of done.
- Write the tests listed for the feature as part of the feature, not later.
- Before declaring a feature done: run `./mvnw verify` and make sure it passes. For features touching Docker, also run `docker compose up --build` and check the acceptance criteria.
- When done, tick the feature's checkbox in `FEATURES.md` and propose a commit message using the one given in the feature.
- If the spec is ambiguous or contradicts itself, **stop and ask**. Do not guess on anything listed under "Invariants" below.
- Do not modify files belonging to a later feature "in advance".

## Tech stack

| Area | Choice |
| --- | --- |
| Language | Java 21 (LTS) |
| Framework | Spring Boot 3.5.x (Web, Validation, Data JPA, Cache, Actuator) |
| Build | Maven via wrapper (`./mvnw`) |
| Database | PostgreSQL 16, schema via Flyway |
| Data access | Spring Data JPA (Hibernate 6) — see "Persistence rules" |
| Cache | Caffeine via Spring Cache abstraction |
| API docs | `springdoc-openapi-starter-webmvc-ui` (version compatible with the Boot version) |
| Metrics | Spring Boot Actuator + `micrometer-registry-prometheus` |
| Frontend | single static `index.html` with vanilla JS, served by Spring |
| Tests | JUnit 5, AssertJ, Mockito, `@WebMvcTest`, Testcontainers (PostgreSQL) |
| Packaging | multi-stage Dockerfile, `docker-compose.yml` (postgres, app, prometheus) |

Ask before adding any dependency not in this table. Lombok is used for boilerplate: `@Slf4j` for logging; `@RequiredArgsConstructor` on services/controllers whose constructor only assigns `final` fields (not on classes like `UrlValidator` that do extra work in the constructor); `@Getter`/`@NoArgsConstructor(access = PROTECTED)` on the JPA entity — never `@Data`, whose generated `equals`/`hashCode`/`toString`/setters are unsafe on a managed, immutable entity. Prefer Java records for DTOs and properties.

## Commands

```bash
./mvnw verify                  # build + all tests (needs Docker running for Testcontainers)
./mvnw test                    # unit + slice tests
docker compose up --build      # full stack: app :8080, management :8081, prometheus :9090
docker compose down -v         # stop and wipe the database volume
```

Local URLs: UI `http://localhost:8080/`, Swagger `http://localhost:8080/swagger-ui.html`, metrics `http://localhost:8081/actuator/prometheus`, Prometheus `http://localhost:9090`.

## Architecture

Single stateless Spring Boot service. Postgres is the source of truth; Caffeine sits in front of it on the redirect path. Actuator runs on a separate management port.

```text
com.example.shortener
├── ShortenerApplication.java
├── controller/
│   ├── ShortUrlController.java
│   └── RedirectController.java
├── service/
│   ├── ShortUrlService.java
│   ├── TargetUrlLookup.java   # @Cacheable, MUST be a separate bean (proxy / self-invocation)
│   ├── UrlValidator.java      # parsing + normalization
│   └── ShortCodeGenerator.java  # affine permutation + base62
├── repository/
│   └── ShortUrlRepository.java  # Spring Data JPA
├── entity/
│   └── ShortUrl.java          # JPA entity, @Immutable
├── dto/
│   ├── CreateShortUrlRequest.java
│   └── ShortUrlResponse.java
├── exception/                 InvalidUrlException, ShortUrlNotFoundException, KeyspaceExhaustedException
├── error/                      GlobalExceptionHandler, ErrorCode
├── metrics/                    ShortenerMetrics, KeyspaceUsageGauge
└── config/                     AppProperties, CodeProperties, CacheConfig, OpenApiConfig
src/main/resources/
├── static/index.html
├── db/migration/V1__create_short_url.sql
└── application.yml
ops/prometheus.yml
```

Package by layer. Controllers are thin: validation annotations + delegate to service. Domain exceptions never reference HTTP; status mapping lives only in `GlobalExceptionHandler`.

## Invariants (do not change without asking)

1. **Code format:** exactly 4 characters from the Base62 alphabet `0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz` (in this order). Case-sensitive. Keyspace `N = 62^4 = 14_776_336`.
2. **Code generation algorithm:** Postgres sequence id → affine permutation → fixed-width Base62:
   `code = base62_4((a * id + c) mod N)` with `gcd(a, N) == 1` (`a` odd and not divisible by 31). No random generation, no hashing of URLs for codes, no retry loops.
3. `a` (`app.code.multiplier`) and `c` (`app.code.offset`) must never change once data exists. Validate `gcd(a, N) == 1` at startup and fail fast.
4. The sequence is `MINVALUE 0 START WITH 0 MAXVALUE 14776335 NO CYCLE`. Exhaustion (SQLState `2200H`) maps to `KeyspaceExhaustedException` → HTTP `503`.
5. **Dedup:** the same normalized URL always returns the existing code (`200 OK`); a new mapping returns `201 Created`. Dedup key is SHA-256 of the normalized URL in column `url_hash`.
6. Mappings are immutable (no update/delete in MVP). That is why the cache needs no invalidation.
7. Redirect is `302 Found` with `Location` and `Cache-Control: no-cache`. Never `301`.
8. All error responses are RFC 9457 `ProblemDetail` with extra properties `errorCode` and `timestamp`. Never leak stack traces, SQL or internal exception messages to clients.
9. Only parameterized queries (JPQL or native). Never concatenate user input into a query.
10. Never use a short code or URL as a metric tag (cardinality).

## Persistence rules (JPA)

- Flyway owns the schema. `spring.jpa.hibernate.ddl-auto: validate` — Hibernate never creates or alters tables.
- `spring.jpa.open-in-view: false`.
- `ShortUrl` entity: `@Entity`, `@Table(name = "short_url")`, `@org.hibernate.annotations.Immutable`, protected no-arg constructor, no setters, no relationships.
- `id` is `@Id` **without `@GeneratedValue`**. It is assigned by the service from `nextval('short_url_id_seq')` (native query), because the code is derived from the id before the insert. Never use Hibernate's sequence generator (its pooled allocation would burn keyspace).
- The insert is a native `@Modifying` query with `ON CONFLICT (url_hash) DO NOTHING`, returning the affected row count. Do not use `save()` for creating short URLs: with an assigned id it triggers `merge()` (extra SELECT) and a duplicate would surface as `DataIntegrityViolationException` with a rollback-only transaction.
- Reads use derived queries or JPQL; the redirect path uses a JPQL projection returning only `targetUrl`.
- Column mappings must match the migration exactly so `validate` passes: `target_url` → `columnDefinition = "text"`, `url_hash` → `byte[]` with `columnDefinition = "bytea"`, `code` → `length = 4`, `created_at` → `Instant`.
- Entities never leave the service layer; controllers return DTO records.
- Repository tests run against real Postgres via Testcontainers (`@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)`). Never use H2: native queries are Postgres-specific.

## Conventions

- Configuration via `@ConfigurationProperties` records annotated `@Validated`; all app properties under the `app.` prefix.
- Short URLs are built from `app.base-url`, never from the request `Host` header.
- Constructor injection only; no field injection.
- Logging: 4xx at INFO/DEBUG without stack trace; 5xx at ERROR with stack trace. Unknown short codes log at DEBUG.
- Logger declaration: Lombok `@Slf4j` on the class (field `log`); no manual `LoggerFactory.getLogger(...)`.
- Frontend renders server data with `textContent`, never `innerHTML`.
- Test names describe behaviour: `returnsExistingCodeWhenSameUrlSubmittedTwice`.
- Commit messages: Conventional Commits (`feat:`, `test:`, `docs:`, `chore:`, `fix:`).
- Comments (including Javadoc) must be small and valuable: at most 3 lines. State the non-obvious "why", not what the code already says.
- No `@param`/`@return`/`@throws` Javadoc that just repeats the method signature or body (e.g. restating a parameter name, or an exception the first `if` already throws visibly). Omit the comment entirely rather than write one that only restates the code.

## Out of scope (do not implement)

Authentication, custom aliases, expiration, deletion, click analytics, rate limiting, malicious URL checks, Redis, Spring Security, Grafana, cloud deployment. These belong in the README "Future work" section only.
