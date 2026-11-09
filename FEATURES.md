and a# FEATURES.md

Implementation plan for the URL shortener, split into sequential features. Read `CLAUDE.md` first: its invariants apply to every feature.

**Rules**

- Implement features strictly in order F0 → F10. Each feature must leave the build green and the app runnable.
- A feature is done only when every acceptance criterion (AC) holds and all listed tests exist and pass.
- `MUST` = required, `SHOULD` = do it unless it costs disproportionate time, `MUST NOT` = forbidden.
- Do not implement anything from a later feature early.

**Status**

- [x] F0 — Project skeleton
- [x] F1 — Short code generator
- [x] F2 — Create short URL
- [x] F3 — Redirect
- [x] F4 — Global error handling
- [x] F5 — Frontend form
- [x] F6 — In-memory read cache
- [x] F7 — OpenAPI / Swagger
- [x] F8 — Metrics (Actuator + Prometheus)
- [x] F9 — Integration tests
  - [x] F10 — README

---

## F0 — Project skeleton

**Depends on:** —
**Goal:** a runnable empty Spring Boot app in Docker with Postgres and the database schema.

**Deliverables**

- `pom.xml` (Java 21, Spring Boot 3.5.x; starters: web, validation, data-jpa, cache, actuator; `postgresql`, `flyway-core`, `flyway-database-postgresql`; test: `spring-boot-starter-test`, `spring-boot-testcontainers`, `org.testcontainers:postgresql`, `org.testcontainers:junit-jupiter`)
- `src/test/java/.../TestcontainersConfiguration.java` (shared `postgres:16-alpine` container with `@ServiceConnection`)
- Maven wrapper, `.gitignore`, `.dockerignore`, `.env.example`
- `Dockerfile`, `docker-compose.yml`
- `src/main/resources/application.yml`
- `src/main/resources/db/migration/V1__create_short_url.sql`
- `config/AppProperties.java`

**Requirements**

- REQ-F0.1 Migration `V1__create_short_url.sql` MUST be exactly:

  ```sql
  CREATE SEQUENCE short_url_id_seq MINVALUE 0 START WITH 0 MAXVALUE 14776335 NO CYCLE;

  CREATE TABLE short_url (
      id          BIGINT      PRIMARY KEY,
      code        VARCHAR(4)  COLLATE "C" NOT NULL,
      target_url  TEXT        NOT NULL,
      url_hash    BYTEA       NOT NULL,
      created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
      CONSTRAINT uq_short_url_code     UNIQUE (code),
      CONSTRAINT uq_short_url_url_hash UNIQUE (url_hash),
      CONSTRAINT ck_short_url_code     CHECK (code ~ '^[0-9A-Za-z]{4}$'),
      CONSTRAINT ck_short_url_url_len  CHECK (char_length(target_url) <= 2048)
  );
  ```

- REQ-F0.2 `Dockerfile` MUST be multi-stage: build stage `eclipse-temurin:21-jdk` running `./mvnw package -DskipTests` with `pom.xml` copied before `src/` (dependency layer caching); runtime stage `eclipse-temurin:21-jre-alpine`, non-root user, `ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]`, `EXPOSE 8080 8081`.
- REQ-F0.3 `docker-compose.yml` MUST define `postgres` (`postgres:16-alpine`, named volume, `pg_isready` healthcheck) and `app` (`build: .`, `depends_on` postgres with `condition: service_healthy`, port `8080:8080`). DB credentials come from env with defaults (`${DB_USER:-shortener}` etc.).
- REQ-F0.4 `AppProperties` MUST be a `@ConfigurationProperties(prefix = "app")` `@Validated` record with `baseUrl` (`@NotBlank`, default `http://localhost:8080`).
- REQ-F0.5 `application.yml` MUST read the datasource from `SPRING_DATASOURCE_*` env vars and set `server.error.include-stacktrace: never`, `spring.jpa.hibernate.ddl-auto: validate`, `spring.jpa.open-in-view: false`.
- REQ-F0.6 Actuator health MUST be reachable; in F0 it may stay on port 8080 (moved in F8).

**Acceptance criteria**

- AC-F0.1 `docker compose up --build` starts both containers; `curl localhost:8080/actuator/health` returns `{"status":"UP"}`.
- AC-F0.2 Table `short_url` and sequence `short_url_id_seq` exist after startup (Flyway history shows V1).
- AC-F0.3 `./mvnw verify` passes.

**Tests:** context-loads `@SpringBootTest` importing `TestcontainersConfiguration` (proves Flyway + Hibernate `validate` work together).

**Commit:** `chore: project skeleton with docker compose and flyway schema`

---

## F1 — Short code generator

**Depends on:** F0
**Goal:** a pure, fully unit-tested component that turns a sequence id into a 4-character code.

**Deliverables**

- `service/ShortCodeGenerator.java`
- `config/CodeProperties.java`
- `exception/KeyspaceExhaustedException.java`
- `ShortCodeGeneratorTest.java`, `CodePropertiesTest.java`

**Requirements**

- REQ-F1.1 Constants: `ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"`, `LENGTH = 4`, `KEYSPACE = 14_776_336L` (62^4).
- REQ-F1.2 `String toCode(long id)`: if `id < 0 || id >= KEYSPACE` throw `KeyspaceExhaustedException`; otherwise compute `v = Math.floorMod(multiplier * id + offset, KEYSPACE)` and encode `v` as fixed-width Base62, most significant digit first, left-padded with `'0'`.
- REQ-F1.3 `CodeProperties` MUST be a `@ConfigurationProperties(prefix = "app.code")` `@Validated` record with `multiplier` (long), `offset` (long, `0 <= offset < KEYSPACE`) and `reserved` (`Set<String>`).
- REQ-F1.4 Startup MUST fail if `gcd(multiplier, KEYSPACE) != 1` or `multiplier` is not in `[1, KEYSPACE)`.
- REQ-F1.5 Defaults in `application.yml`: `multiplier: 9132277`, `offset: 4861105`, `reserved: [docs, help, info, home, null, test, root, user, html]`. Overridable via `APP_CODE_MULTIPLIER`, `APP_CODE_OFFSET`.
- REQ-F1.6 `ShortCodeGenerator` MUST NOT access the database; it is a pure function of id and config.

**Acceptance criteria**

- AC-F1.1 Every generated code has length 4 and only alphabet characters.
- AC-F1.2 Distinct ids give distinct codes (bijection).
- AC-F1.3 `toCode(KEYSPACE)` and `toCode(-1)` throw `KeyspaceExhaustedException`.
- AC-F1.4 Invalid multiplier (e.g. `2`, `31`, `0`) fails validation.

**Tests**

- Length and alphabet for ids `0`, `1`, `61`, `62`, `KEYSPACE - 1`, and 200 random ids.
- Bijection: 1 000 000 random distinct ids → 1 000 000 distinct codes.
- Full-keyspace bijection test over all 14 776 336 ids, annotated `@Tag("slow")`.
- Exhaustion boundaries; invalid multipliers via `@ParameterizedTest`.

**Commit:** `feat: short code generator with affine permutation and base62`

---

## F2 — Create short URL

**Depends on:** F1
**Goal:** `POST /api/v1/short-urls` creates (or returns the existing) short URL.

**Deliverables**

- `service/UrlValidator.java`, `exception/InvalidUrlException.java`
- `entity/ShortUrl.java` (JPA entity)
- `repository/ShortUrlRepository.java` (`JpaRepository<ShortUrl, Long>`)
- `service/ShortUrlService.java`, `service/CreateResult.java` (record: `ShortUrl shortUrl, boolean created`)
- `controller/ShortUrlController.java`, `dto/CreateShortUrlRequest.java`, `dto/ShortUrlResponse.java`

**Requirements**

- REQ-F2.1 Request DTO: `{ "url": string }`, `@NotBlank`, `@Size(max = 2048)`.
- REQ-F2.2 `UrlValidator.normalize(String raw)` MUST, in order: trim; parse with `java.net.URI` (reject on `URISyntaxException`); require scheme `http` or `https` (case-insensitive); require non-empty host; reject if host equals the host of `app.base-url`; lowercase scheme and host; drop default port (`:80` for http, `:443` for https); keep path, query and fragment unchanged. Any violation throws `InvalidUrlException` with a human-readable message.
- REQ-F2.3 `ShortUrl` entity MUST follow the "Persistence rules" in `CLAUDE.md`: `@Immutable`, assigned `@Id` without `@GeneratedValue`, fields `id`, `code`, `targetUrl` (`columnDefinition = "text"`), `urlHash` (`byte[]`, `columnDefinition = "bytea"`), `createdAt` (`Instant`), protected no-arg constructor, getters only.
- REQ-F2.4 `ShortUrlRepository` MUST declare exactly:

  ```java
  Optional<ShortUrl> findByCode(String code);
  Optional<ShortUrl> findByUrlHash(byte[] urlHash);

  @Query(value = "SELECT nextval('short_url_id_seq')", nativeQuery = true)
  long nextId();

  @Modifying
  @Query(value = """
      INSERT INTO short_url (id, code, target_url, url_hash, created_at)
      VALUES (:id, :code, :targetUrl, :urlHash, :createdAt)
      ON CONFLICT (url_hash) DO NOTHING
      """, nativeQuery = true)
  int insertIfAbsent(long id, String code, String targetUrl, byte[] urlHash, Instant createdAt);
  ```

- REQ-F2.5 `ShortUrlService.create(String rawUrl)` MUST be `@Transactional` and:
  1. normalize the URL;
  2. compute `urlHash = SHA-256(normalizedUrl as UTF-8)`;
  3. `findByUrlHash`; if present return `CreateResult(existing, false)`;
  4. `nextId()`, `toCode(id)`; if the code is in `app.code.reserved`, call `nextId()` again;
  5. `insertIfAbsent(...)` with `createdAt = Instant.now()`;
  6. if it returned `0` (lost a race on the same URL), `findByUrlHash` again and return `CreateResult(existing, false)`;
  7. if it returned `1`, `findByCode(code)` and return `CreateResult(saved, true)`.

  MUST NOT use `repository.save()` for creation.
- REQ-F2.6 An exception from `nextId()` whose root `SQLException` has SQLState `2200H` MUST be translated to `KeyspaceExhaustedException` in the service (use `NestedExceptionUtils.getMostSpecificCause`).
- REQ-F2.7 Response DTO: `{ "code", "shortUrl", "targetUrl", "createdAt" }`, where `shortUrl = app.base-url + "/" + code`. The controller maps the entity to the DTO; entities MUST NOT be serialized directly.
- REQ-F2.8 HTTP: `201 Created` with header `Location: /api/v1/short-urls/{code}` when `created = true`, otherwise `200 OK`.

**Acceptance criteria**

- AC-F2.1 `curl -X POST localhost:8080/api/v1/short-urls -H 'Content-Type: application/json' -d '{"url":"https://www.rebuy.de/kaufen"}'` → `201` with a 4-char `code`.
- AC-F2.2 Repeating the same request → `200` with the same `code`.
- AC-F2.3 `HTTPS://WWW.REBUY.DE:443/kaufen` → `200` with the same `code` as AC-F2.1 (normalization).
- AC-F2.4 `javascript:alert(1)`, `ftp://x.org`, `not a url`, `https://` → rejected (status mapping is finalized in F4; until then any 4xx is acceptable).

**Tests**

- `UrlValidatorTest` (`@ParameterizedTest`): valid URLs, each rejection rule, normalization cases, path case preserved (`/A` ≠ `/a`).
- `ShortUrlServiceTest` (Mockito): returns existing on hash hit; skips reserved codes; handles `insertIfAbsent` returning `0` by re-reading; maps SQLState `2200H` to `KeyspaceExhaustedException`.
- `ShortUrlRepositoryTest` (`@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` + Testcontainers): `insertIfAbsent` returns `1` then `0` for the same hash; `findByCode` / `findByUrlHash` round trip; `nextId()` increments.

**Commit:** `feat: create short url endpoint with validation and dedup`

---

## F3 — Redirect

**Depends on:** F2
**Goal:** opening a short URL redirects to the target.

**Deliverables**

- `controller/RedirectController.java`
- `service/TargetUrlLookup.java`
- `exception/ShortUrlNotFoundException.java`
- `GET /api/v1/short-urls/{code}` added to `ShortUrlController`

**Requirements**

- REQ-F3.1 Route `GET /{code:[0-9A-Za-z]{4}}`. Paths that don't match the regex MUST fall through to Spring's default 404 handling.
- REQ-F3.2 Found → `302 Found`, `Location: <targetUrl>`, `Cache-Control: no-cache`, empty body.
- REQ-F3.3 Not found → throw `ShortUrlNotFoundException`.
- REQ-F3.4 Lookup of `targetUrl` by code MUST go through `TargetUrlLookup.findTargetUrl(String code)` (returns `null` when absent), a separate Spring bean — it will be annotated `@Cacheable` in F6. It calls a JPQL projection added to the repository: `@Query("select s.targetUrl from ShortUrl s where s.code = :code") Optional<String> findTargetUrlByCode(String code);` and returns `.orElse(null)`.
- REQ-F3.5 `GET /api/v1/short-urls/{code}` returns the `ShortUrlResponse` or throws `ShortUrlNotFoundException`.
- REQ-F3.6 MUST NOT use `301`, `307` or `308`.

**Acceptance criteria**

- AC-F3.1 `curl -i localhost:8080/<code>` → `302` with correct `Location`.
- AC-F3.2 Opening the short URL in a browser lands on the target page.
- AC-F3.3 Unknown 4-char code → 404; `/abc` and `/abcde` → 404.

**Tests:** `@WebMvcTest` for both controllers (found, not found, invalid format).

**Commit:** `feat: redirect endpoint`

---

## F4 — Global error handling

**Depends on:** F3
**Goal:** every error returns a consistent RFC 9457 `ProblemDetail`.

**Deliverables**

- `error/GlobalExceptionHandler.java` (`@RestControllerAdvice`, extends `ResponseEntityExceptionHandler`)
- `error/ErrorCode.java` (enum)
- `src/main/resources/static/404.html` (or equivalent) for browser requests

**Requirements**

- REQ-F4.1 Response body shape:

  ```json
  {
    "type": "urn:problem:<kebab-case-error-code>",
    "title": "<short title>",
    "status": 400,
    "detail": "<human-readable message>",
    "instance": "/request/path",
    "errorCode": "INVALID_URL",
    "timestamp": "2026-10-30T17:15:00Z"
  }
  ```

- REQ-F4.2 Error catalog (MUST be implemented exactly):

  | Exception | Status | errorCode | Log level |
  | --- | --- | --- | --- |
  | `MethodArgumentNotValidException` | 400 | `VALIDATION_FAILED` (+ `errors: [{field, message}]`) | INFO |
  | `InvalidUrlException` | 400 | `INVALID_URL` | INFO |
  | `HttpMessageNotReadableException` | 400 | `MALFORMED_REQUEST` | INFO |
  | `ShortUrlNotFoundException` | 404 | `SHORT_URL_NOT_FOUND` | DEBUG |
  | `NoResourceFoundException` | 404 | `NOT_FOUND` | DEBUG |
  | `HttpRequestMethodNotSupportedException` | 405 | `METHOD_NOT_ALLOWED` | INFO |
  | `HttpMediaTypeNotSupportedException` | 415 | `UNSUPPORTED_MEDIA_TYPE` | INFO |
  | `KeyspaceExhaustedException` | 503 | `KEYSPACE_EXHAUSTED` | ERROR |
  | `DataAccessException` | 503 | `STORAGE_UNAVAILABLE` | ERROR |
  | any other `Exception` | 500 | `INTERNAL_ERROR` (generic `detail`) | ERROR with stack trace |

- REQ-F4.3 For a 404 on the redirect route, if the request `Accept` header prefers `text/html`, return the static HTML 404 page with status 404; otherwise return `ProblemDetail`.
- REQ-F4.4 `detail` MUST NOT contain stack traces, SQL, class names or messages of unexpected exceptions.
- REQ-F4.5 4xx MUST be logged without stack trace.

**Acceptance criteria**

- AC-F4.1 Every row of the catalog is reachable and returns the specified status and `errorCode`.
- AC-F4.2 Invalid JSON body → 400 `MALFORMED_REQUEST`, not 500.

**Tests:** `@WebMvcTest` covering each catalog row (mock the service to throw), plus the HTML-vs-JSON 404 case.

**Commit:** `feat: global exception handler with problem details`

---

## F5 — Frontend form

**Depends on:** F4
**Goal:** a user can shorten a URL in the browser without any tools.

**Deliverables:** `src/main/resources/static/index.html` (HTML, inline CSS and JS in one file).

**Requirements**

- REQ-F5.1 Form with `<input type="url" required maxlength="2048">` and a submit button.
- REQ-F5.2 If the input has no scheme, prepend `https://` before sending.
- REQ-F5.3 Submit via `fetch('/api/v1/short-urls', { method: 'POST', headers: {'Content-Type': 'application/json'}, body })`.
- REQ-F5.4 On success show `shortUrl` as a clickable link plus a Copy button (`navigator.clipboard.writeText`).
- REQ-F5.5 On error show `detail` from the `ProblemDetail` response.
- REQ-F5.6 Disable the submit button while a request is in flight.
- REQ-F5.7 Insert server data with `textContent` / attribute setters only; MUST NOT use `innerHTML`.
- REQ-F5.8 No frameworks, no build step, no external CDN.

**Acceptance criteria**

- AC-F5.1 At `http://localhost:8080/` entering `rebuy.de` shows a working short link.
- AC-F5.2 Entering an invalid URL shows the server's error message.
- AC-F5.3 Clicking the short link redirects to the target.

**Tests:** manual check of the ACs; no automated frontend tests.

**Commit:** `feat: html form for shortening urls`

---

## F6 — In-memory read cache

**Depends on:** F5
**Goal:** redirects for known codes are served from memory without hitting the database.

**Deliverables:** `config/CacheConfig.java` (`@EnableCaching`), cache settings in `application.yml`, dependency `com.github.ben-manes.caffeine:caffeine`.

**Requirements**

- REQ-F6.1 Cache name `targetUrls`; key = code; value = target URL `String`.
- REQ-F6.2 Config: `spring.cache.type: caffeine`, `spring.cache.caffeine.spec: maximumSize=100000,expireAfterAccess=1h,recordStats`.
- REQ-F6.3 `TargetUrlLookup.findTargetUrl` annotated `@Cacheable(cacheNames = "targetUrls", key = "#code", unless = "#result == null")` — negative results MUST NOT be cached.
- REQ-F6.4 Read-through only: `create()` MUST NOT touch the cache. The first redirect for a newly created code is always a cache miss and populates the cache itself via `@Cacheable` — write-through at creation time was considered and dropped: it only pre-warms an entry that `expireAfterAccess` may well have evicted again before the code is first used, at the cost of a cache write on every creation (even ones never redirected to) and extra transaction-commit-ordering complexity in `ShortUrlService`.
- REQ-F6.5 MUST NOT call the `@Cacheable` method from inside the same bean.

**Acceptance criteria**

- AC-F6.1 Second redirect for the same code does not execute SQL.
- AC-F6.2 Unknown code requested, then created → next redirect works (no stale negative entry).

**Tests:** test with a spy/mock repository (or SQL counting) proving AC-F6.1 and AC-F6.2.

**Commit:** `feat: caffeine read cache for redirects`

---

## F7 — OpenAPI / Swagger

**Depends on:** F6
**Goal:** the API is self-documented and explorable.

**Deliverables:** dependency `springdoc-openapi-starter-webmvc-ui`, `config/OpenApiConfig.java`, annotations on controllers and DTOs.

**Requirements**

- REQ-F7.1 `@OpenAPIDefinition` with title "URL Shortener API", version, description, and `servers` = `app.base-url`.
- REQ-F7.2 Every endpoint has `@Tag` (on controller) and `@Operation(summary = ...)`.
- REQ-F7.3 Every status in the F4 catalog relevant to an endpoint is declared via `@ApiResponse` with `ProblemDetail` schema.
- REQ-F7.4 DTO fields have `@Schema(description, example)`.
- REQ-F7.5 The redirect operation's description MUST note that "Try it out" fails in the browser because of CORS on the cross-origin redirect, and that `curl -i` should be used.
- REQ-F7.6 Swagger UI at `/swagger-ui.html`, spec at `/v3/api-docs`; disable-able via `springdoc.swagger-ui.enabled`.

**Acceptance criteria**

- AC-F7.1 Swagger UI opens and shows all three endpoints with examples.
- AC-F7.2 "Try it out" on create returns `201`.

**Tests:** smoke test that `/v3/api-docs` returns 200 and contains `/api/v1/short-urls`.

**Commit:** `docs: openapi documentation with springdoc`

---

## F8 — Metrics (Actuator + Prometheus)

**Depends on:** F7
**Goal:** operational and business metrics are scraped by Prometheus.

**Deliverables:** dependency `micrometer-registry-prometheus`, `metrics/ShortenerMetrics.java`, `metrics/KeyspaceUsageGauge.java`, `ops/prometheus.yml`, `prometheus` service in compose.

**Requirements**

- REQ-F8.1 Management on port 8081; exposed endpoints exactly `health,info,prometheus,metrics`; health probes enabled; common tag `application=url-shortener`; `percentiles-histogram` enabled for `http.server.requests`.
- REQ-F8.2 Custom meters (Micrometer names):

  | Name | Type | Tags | Incremented when |
  | --- | --- | --- | --- |
  | `shortener.urls.created` | counter | `result=created\|existing` | each successful create call — exposed as `shortener_urls_total` on the Prometheus endpoint: the modern Prometheus client (used by `micrometer-registry-prometheus` 1.17.x) reserves `_created` as a trailing metric-name suffix (alongside `_total`/`_bucket`/`_info`) and strips it, so no dot-segment arrangement keeps the literal word "created" in the final exposed name. Not a bug in this code; verified by decompiling `PrometheusNaming`/`PrometheusNamingConvention`. |
  | `shortener.redirects` | counter | `result=found\|not_found` | each redirect request |
  | `shortener.errors` | counter | `code=<ErrorCode>` | each response from `GlobalExceptionHandler` |
  | `shortener.keyspace.used.ratio` | gauge | — | value = `last_value / 14776336`, refreshed every 60 s by `@Scheduled`, not per scrape |

- REQ-F8.3 MUST NOT tag any meter with a short code or URL.
- REQ-F8.4 Compose: app publishes `127.0.0.1:8081:8081`; `prometheus` (`prom/prometheus`, port 9090) mounts `ops/prometheus.yml` scraping `app:8081/actuator/prometheus` every 15 s; app healthcheck uses `wget -qO- http://localhost:8081/actuator/health/readiness`.
- REQ-F8.5 Cache metrics from F6 MUST appear (`cache_gets_total{cache="targetUrls"}`).

**Acceptance criteria**

- AC-F8.1 Prometheus UI → Targets shows the app `UP`.
- AC-F8.2 After one create and one redirect, `curl localhost:8081/actuator/prometheus` contains `shortener_urls_total` (see REQ-F8.2 note), `shortener_redirects_total`, `shortener_keyspace_used_ratio`, `cache_gets_total`. `cache_gets_total` only appears once the `targetUrls` cache has been created — `spring.cache.cache-names: targetUrls` forces this eagerly at startup instead of on first (lazy) use, so cache metrics bind immediately rather than only after the first redirect.
- AC-F8.3 `/actuator` is not reachable on port 8080.

**Tests:** `@SpringBootTest` smoke test asserting the custom meters are registered.

**Commit:** `feat: prometheus metrics and business counters`

---

## F9 — Integration tests

**Depends on:** F8
**Goal:** end-to-end confidence on a real Postgres.

**Deliverables:** `ShortenerIntegrationTest.java`, `ConcurrencyIntegrationTest.java` (Testcontainers is already set up since F0).

**Requirements**

- REQ-F9.1 Reuse the shared `TestcontainersConfiguration` from F0; do not start additional containers.
- REQ-F9.2 Cover: Flyway migration applies; create → redirect round trip via `TestRestTemplate` or `MockMvc` with redirects disabled; dedup returns same code; validation error returns `ProblemDetail`.
- REQ-F9.3 Concurrency: 50 threads submitting the same URL get exactly one distinct code and one row; 50 threads submitting 50 different URLs get 50 distinct codes.
- REQ-F9.4 The concurrency test MUST NOT be `@Transactional` (each thread needs its own committed transaction). Drives each thread through `MockMvc` rather than `TestRestTemplate`: Boot 4.1.2's `TestRestTemplateTestAutoConfiguration` currently fails context startup (`@ConditionalOnMissingBean` can't deduce its own bean type) — a framework bug, not fixable from application code. `MockMvc` still exercises the real controller → service → DB path per thread with a real committed transaction, which is what this test actually needs.

**Acceptance criteria**

- AC-F9.1 `./mvnw verify` passes from a clean clone with only Docker installed.

**Commit:** `test: integration tests with testcontainers`

---

## F10 — README

**Depends on:** F9
**Goal:** a reviewer can run, understand and evaluate the project in five minutes.

**Deliverables:** `README.md`.

**Requirements** — sections in this order:

1. One-paragraph summary.
2. Quick start: `docker compose up --build`; links to UI, Swagger, `/actuator/prometheus`, Prometheus.
3. `curl` examples for create (201 and 200) and redirect (`curl -i`).
4. Architecture: short description + package layout.
5. Technical decisions: code generation (sequence + affine permutation + Base62) and why not random or hash truncation (birthday collisions at ~4.5k links for 62^4); Base62 vs Base64url; dedup; `302` vs `301`; Caffeine; JPA with assigned ids and a native `ON CONFLICT` insert (why not `save()` / `@GeneratedValue`); `ProblemDetail`; separate management port.
6. Limitations: 14 776 336 codes maximum; codes are enumerable — do not shorten secret URLs; open redirect by nature.
7. Running tests: `./mvnw verify` (requires Docker).
8. Future work: click analytics, expiration, custom aliases, rate limiting, Safe Browsing, Redis, Feistel permutation, CI.

**Acceptance criteria**

- AC-F10.1 Following the README on a clean clone produces a working app without extra steps.
- AC-F10.2 Every command in the README has been executed and works.

**Commit:** `docs: readme with setup and technical decisions`
