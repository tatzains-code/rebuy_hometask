# CLAUDE.md

Guidance for AI coding assistants working in this repository.

## Project

URL shortener MVP for a take-home challenge (rebuy), now feature-complete (see `README.md`). This
file carries forward the rules that keep any further changes consistent with the existing design.

## Working agreement

- **Never create a git commit, or create/delete a branch, without an explicit instruction in that
  same conversation turn to do so.** Drafting or editing files is not authorization to commit or
  branch — those are separate, visible actions that need their own explicit go-ahead. If in doubt,
  make the file changes and ask before doing anything git-related.
- Before making a change, restate your understanding of its scope and confirm it — do not guess at
  an ambiguous requirement.
- Keep changes scoped to exactly what was asked. Resist refactoring unrelated code, adding tests
  for things that already have them, or building adjacent "nice to have" behavior — note it as
  future work instead of doing it.
- Write the tests for a new feature as part of that feature, not after.
- Before declaring something done: run `./mvnw verify` (a scoped `./mvnw test` first is fine for a
  quick signal) and make sure it passes.
- If a request is ambiguous or conflicts with an invariant below, stop and ask rather than guessing.

## Current state

The MVP covers: create short URL, redirect, global error handling, a frontend form, a Caffeine
read cache, OpenAPI docs, Prometheus metrics, and integration tests (F0–F10, all done). `README.md`
has the full design rationale; `FEATURES.md` is the build history — useful background, not an
active task list.

- `scripts/seed.sh` populates a handful of sample short URLs against a running instance — handy
  after `docker compose up --build`.
- `postman/` has a small collection covering the core flows (create, dedup, redirect, 404,
  validation error, health) for manual testing against a local environment.

## Tech stack (as actually running)

| Area | Choice |
| --- | --- |
| Language | Java 25 |
| Framework | Spring Boot 4.1.2 (webmvc, validation, data-jpa, cache, actuator starters) |
| Build | Maven via wrapper (`./mvnw`) |
| Database | PostgreSQL 16, schema via Flyway |
| Data access | Spring Data JPA (Hibernate 7) |
| Cache | Caffeine via Spring Cache abstraction |
| API docs | `springdoc-openapi-starter-webmvc-ui` 3.x |
| Metrics | Spring Boot Actuator + `micrometer-registry-prometheus` |
| Frontend | single static `index.html`, vanilla JS, no build step |
| Tests | JUnit 5, AssertJ, Mockito, `@WebMvcTest`, Testcontainers (PostgreSQL) |

Ask before adding a dependency not already in `pom.xml`. Lombok conventions already in use:
`@Slf4j` for logging, `@RequiredArgsConstructor` on services/controllers whose constructor only
assigns `final` fields, records preferred for DTOs and config. Never `@Data` on the JPA entity.

## Hard invariants (do not change without asking)

1. **Code format:** exactly 4 Base62 characters (`0-9A-Za-z`), case-sensitive, keyspace
   `62^4 = 14_776_336`. Fixed by design — a feature needing a different code shape or a larger
   keyspace is out of scope for a quick addition; flag it instead of changing the format.
2. **Code generation:** sequence id → affine permutation → Base62. No random generation, no
   hashing, no retry loops. `multiplier`/`offset` never change once data exists.
3. Mappings are currently immutable (no update/delete). A feature that needs mutation (expiration,
   alias editing) is a deliberate, explicit change to that assumption — call it out, don't bolt it
   on silently.
4. All error responses are RFC 9457 `ProblemDetail` via the single `GlobalExceptionHandler`. A new
   failure mode gets a row added to its catalog, never a one-off try/catch in a controller.
5. Only parameterized queries (JPQL or native). Never concatenate user input into SQL.
6. Never use a short code or a URL as a metric tag (cardinality).
7. Flyway owns the schema (`spring.jpa.hibernate.ddl-auto: validate`). A new column or table needs
   a new `V{n}__...sql` migration — `V1` is already applied and must never be edited.
8. Constructor injection only. DTOs stay records. The service returns entities to the controller,
   which maps them to a DTO before returning — entities are never serialized/returned directly
   from an endpoint.

## Out of scope unless explicitly requested

Authentication, rate limiting, malicious-URL screening, Redis, Spring Security, cloud deployment,
Kafka/event publishing. These are listed in the README's "What I'd add with more time" — if one of
them is explicitly asked for, it's now in scope; otherwise leave it alone.

## Conventions

- Commit messages: Conventional Commits (`feat:`, `test:`, `docs:`, `fix:`, `chore:`) — proposed by
  the assistant, created only when explicitly asked (see "Working agreement" above).
- Comments: small and valuable only — state a non-obvious "why", never restate the code.
- Test names describe behaviour, e.g. `returnsExistingCodeWhenSameUrlSubmittedTwice`.
- Logging: 4xx at INFO/DEBUG without a stack trace; 5xx at ERROR with one.
