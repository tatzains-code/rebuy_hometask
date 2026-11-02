# URL Shortener

A small Spring Boot service that turns a long URL into a short, 4-character link and redirects
anyone who opens it back to the original. You can use it through the web form or the REST API
directly. Codes are generated deterministically from a database sequence (no randomness, no
retry loops), the same URL always gets the same short code, and every error response comes back
in a consistent, machine-readable shape.

## Quick start

```bash
docker compose up --build
```

This starts Postgres, the app, and Prometheus together. Once it's up:

| What | Where |
| --- | --- |
| Web form | http://localhost:8080/ |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI spec | http://localhost:8080/v3/api-docs |
| App metrics (Prometheus format) | http://localhost:8081/actuator/prometheus |
| Prometheus UI | http://localhost:9090 |

To stop everything and wipe the database: `docker compose down -v`.

## Using the API

Create a short URL:

```bash
curl -i -X POST localhost:8080/api/v1/short-urls \
  -H 'Content-Type: application/json' \
  -d '{"url":"https://www.google.com/search?q=spring+boot"}'
```

The first time, you get `201 Created` with the new short URL. Submit the exact same URL again
and you get `200 OK` back with the *same* code — no duplicate is created:

```bash
curl -i -X POST localhost:8080/api/v1/short-urls \
  -H 'Content-Type: application/json' \
  -d '{"url":"https://www.google.com/search?q=spring+boot"}'
```

Follow a short link (use `-i` so you can see the redirect instead of silently following it):

```bash
curl -i localhost:8080/<code>
```

You'll get `302 Found` with a `Location` header pointing at the original URL.

## Architecture

A single, stateless Spring Boot service. Postgres is the only source of truth; Caffeine sits in
front of it purely to speed up repeated redirects. The code is organized by layer, not by
feature:

```text
com.example.shortener
├── ShortenerApplication.java
├── controller/   REST endpoints — thin, delegate everything to service/
├── service/      business logic: validation, creation, caching, code generation
├── repository/   Spring Data JPA, one repository, all native/JPQL queries
├── entity/       the one JPA entity (ShortUrl)
├── dto/          request/response records the API actually exposes
├── exception/    domain exceptions — never reference HTTP status codes
├── error/        the one place that maps exceptions to HTTP responses
├── metrics/      Micrometer counters/gauges
└── config/       typed configuration, cache, OpenAPI
src/main/resources/
├── static/       index.html (the form) and 404.html
├── db/migration/ the one Flyway migration
└── application.yml
ops/prometheus.yml
```

## Assumptions

A few things were deliberately decided rather than left ambiguous:

- **"Same URL" means the exact normalized string** — no trailing-slash, query-order, or
  tracking-param canonicalization.
- **Only `http`/`https`, never a link back at this service.**
- **No authentication, deletion, or expiration** — short URLs are created freely and live forever.
- **Codes are case-sensitive.**
- **Single running instance** — the Caffeine cache isn't shared across instances.
- **Java 25 and Spring Boot 4.1.2**, not an older LTS pairing.

## Technical decisions

**Code generation: a sequence, an affine permutation, and Base62 — not random, not a hash.**
Each code comes from a Postgres sequence id run through `code = base62_4((a·id + c) mod N)`,
where `N = 62⁴ = 14,776,336` and `a` is chosen to be coprime with `N` so the mapping is a true
bijection (every id gets a distinct code, and every code maps back to exactly one id). Two
simpler-looking alternatives were deliberately rejected:
- **Random generation** would need a collision-check-and-retry loop against the database on every
  single creation, with no upper bound on how many retries you might need as the keyspace fills up.
- **Hashing the URL and truncating it** looks appealing, but the birthday bound bites early: with
  only `62⁴ ≈ 14.8M` possible values, you'd expect your first collision at around **4,500** stored
  links (`≈1.18·√N`), which is nowhere near enough headroom for a real service.

The sequence+permutation approach needs neither — no retries, no collision checks, and codes still
look unpredictable without actually being random.

**Base62, not Base64url.** Both give a dense, 4-character code for this keyspace size, but Base64url
needs `+`, `-`, `_` or percent-encoding to stay URL-safe depending on the variant, while Base62
(digits + upper + lower case letters only) avoids every RFC 3986 reserved character and `%` by
construction — already guaranteed URL-safe with zero encoding concerns, at the cost of nothing
but a slightly different alphabet.

**Dedup by content, not by request.** The normalized URL's SHA-256 hash is stored with a unique
database constraint. Submit the same URL twice — even from different requests, even concurrently —
and you always get back the same code, never a duplicate row. That's a deliberate trade for
keyspace, not analytics: two unrelated submissions of the same URL can't be told apart afterward.

**`302`, never `301`.** A `301` tells browsers "cache this redirect forever," which would make any
future change (expiring links, rotating a target) impossible to roll out cleanly, and would let a
CDN or browser quietly stop every future click from reaching the service at all. `302` keeps every
redirect decision — and every click — live on the server, where it belongs for a service like this.

**Caffeine, read-through only.** The in-memory cache sits only on the read path (`@Cacheable` on
the lookup used by redirects); creating a URL never touches the cache. A write-through version was
tried and deliberately removed: it only ever saved one database query (for a redirect happening
immediately after creation), while costing a cache write on every single creation — including ones
that are never followed — plus extra complexity to avoid caching a row before its transaction
committed. Read-through alone already gives correct, cold-cache-safe behavior with far less code.

**An assigned id and a native `INSERT ... ON CONFLICT`, not `save()` or `@GeneratedValue`.** The
short code is *derived from* the row's id, so the id must be known **before** the row is written —
incompatible with Hibernate's own id generators, which hand out ids in batches and don't guarantee
they'll be used in order. The id comes straight from `nextval(...)` instead, and the insert is a
native, parameterized `INSERT ... ON CONFLICT (url_hash) DO NOTHING` rather than `repository.save()`
— `save()` on an entity with a pre-assigned id triggers a `merge()` (an extra `SELECT` before every
insert), and a conflicting row would surface as a thrown `DataIntegrityViolationException` on a
now-rollback-only transaction instead of the plain "0 rows affected" this code checks for instead.

**`ProblemDetail` (RFC 9457) for every error, from one place.** Every error — ours or Spring's own
(validation failures, bad JSON, wrong HTTP method, …) — comes back through a single
`GlobalExceptionHandler`, in the same JSON shape, with the same `errorCode`/`timestamp` fields.
Domain exceptions themselves never mention HTTP status codes; only that one class decides them.

**A separate management port.** Actuator and Prometheus metrics are served on port `8081`, not the
public `8080`. Operational data about the service (and the ability to probe its health) isn't
something the public API port needs to expose at all.

## Limitations

- **14,776,336 codes, maximum.** `62⁴` is the entire keyspace. Once it's exhausted, creating a new
  short URL fails with `503` rather than silently breaking the bijection.
- **Codes are enumerable, not secret.** They come from a known, deterministic permutation, not from
  a cryptographically random generator — don't use this to "hide" a URL from anyone motivated enough
  to try every 4-character code.
- **It's an open redirect, by design.** The service will redirect to whatever URL was stored for a
  code, with no destination allow-list. That's inherent to what a URL shortener *is* — worth knowing
  if you're integrating it into something that cares about that, not a bug to fix here.

## Running the tests

```bash
./mvnw verify
```

Requires Docker running (tests spin up a real Postgres via Testcontainers — same database engine as
production, no H2 substitutions). This also runs the full test suite from a clean clone with no
other setup needed.

## What I'd add with more time

- **Click analytics** — a counter (or a small events table) per short URL, so creators can see how
  often their link was actually followed.
- **Expiration** — an optional TTL on a short URL, with a scheduled sweep to free up its code.
- **Custom aliases** — letting a user pick their own code instead of always getting a generated one.
- **Rate limiting** — per-IP or per-API-key limits on creation, to stop the keyspace being burned
  through by one abusive client.
- **Safe Browsing-style checks** — screening the destination URL against a malware/phishing list
  before agreeing to shorten it.
- **Redis instead of Caffeine** — once this runs as more than one instance, an in-process cache stops
  being enough; the read-through `@Cacheable` design carries over to a distributed cache with no
  change to the calling code.
- **A Feistel permutation instead of the affine one** — still a bijection with no collision checks,
  but with a less obviously "sequential-looking" output than `a·id + c`.
- **CI** — a GitHub Actions workflow running `./mvnw verify` on every push, so the Testcontainers
  suite (including the concurrency tests) gates every change automatically.
