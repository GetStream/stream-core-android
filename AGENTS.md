# Repository Guidelines

Guidance for AI coding agents (Copilot, Cursor, Aider, Claude, etc.) working in Stream’s Android Core repo. Humans are welcome too; tone is optimised for tools.

### Repository purpose
This project houses **Stream Android Core**, the shared runtime used by Stream Chat, Video, and Feeds SDKs. It concentrates connection/session lifecycle, auth/token plumbing, job orchestration, retry/backoff logic, batching utilities, logging, and other cross-product primitives. Treat it as infrastructure: focus on correctness, binary compatibility, and low regression risk.

### Tech & toolchain
- Language: Kotlin (JVM target 11 inside Gradle; toolchain 17 for builds)
- Android min/target/compile: see `io.getstream.core.Configuration`
- Coroutines: kotlinx 1.8+
- Networking: OkHttp, Retrofit, Moshi (codegen via KSP)
- Build: Gradle Kotlin DSL, shared logic in `buildSrc/`
- Static analysis: Spotless + ktfmt, Detekt, Kotlin explicit-api, Kover coverage
- Tests: JUnit4, MockK, Robolectric, kotlinx-coroutines-test, MockWebServer

## Project structure
- `stream-android-core/` – main library (models, socket/session, token handling, processors, batching, retry, queueing)
- `stream-android-core-annotations/` – annotations + KSP helpers consumed by core
- `stream-android-core-lint/` – lint checks shipped with the library
- `app/` – demo/debug client used for manual verification
- `buildSrc/` – centralised Gradle plugins & configuration (publishing, dependency versions, coverage)
- `config/` – license templates, detekt rules, formatting configs
- `scripts/` – publishing and tooling helpers

> Modules are published; avoid leaking impl-only types across module boundaries without updating versioning docs.

## Build, test, and validation
- Format & headers: `./gradlew spotlessApply`
- Static analysis: `./gradlew detektAll` (or module-specific `:module:detekt`)
- JVM/unit tests (core module): `./gradlew :stream-android-core:test`
- Full verification: `./gradlew check`
- Sample app install: `./gradlew :app:installDebug` (requires device/emulator)
- Coverage: `./gradlew koverHtmlReport`

Prefer running module-scoped tasks when iterating (`:stream-android-core:test`, `:stream-android-core:detekt`) to keep the cycle fast. All public changes should pass `./gradlew check` before PR.

## Coding principles
- **Threading**: Coroutines wrap most workflows; keep blocking I/O off main thread. Respect existing dispatcher usage and structured concurrency.
- **State propagation**: Connection state, batch processing, and retry flows feed `StreamSubscriptionManager`. Maintain idempotency and guard callback ordering (see `StreamSocketSession`).
- **Binary compatibility**: `explicitApi()` is enabled. Avoid signature breaking changes to public/internal APIs without coordinating version bumps.
- **Logging**: Use tagged `StreamLogger`. Keep error logs actionable; avoid leaking secrets/tokens.
- **Configurability**: Many processors expose factories; prefer adding configuration points over ad-hoc branching.

## Style & conventions
- Kotlin official code style (Spotless enforces). 4 spaces, no wildcard imports.
- Limit `internal` surface area; favour private helpers where possible.
- Backtick test names for clarity (``fun `serial queue delivers items in order`()``).
- Public APIs require KDoc; significant internal flows get concise comments explaining the “why” (avoid narrating what the code already states).
- Keep licence headers intact; regenerate via `./gradlew generateLicense` if the year changes.

## Testing guidance
- Unit tests live under each module’s `src/test/java`. Use MockK and coroutines-test to control timing.
- Networking/socket flows: exercise with MockWebServer or fake listeners; ensure heartbeats, retries, and cleanup happen (see `StreamSocketSessionTest`).
- When touching concurrency tools (batcher, single-flight, retry), add deterministic tests that cover edge cases like cancellation, dedupe, and failure retries.
- Prefer lightweight Robolectric tests only when Android primitives are involved; otherwise keep pure JVM.
- Always run `./gradlew :stream-android-core:test` for touched code; broaden to `./gradlew check` before publishing.

## Documentation & comments
- Update `README.md` if you change major workflows or public setup code.
- Keep processor/queue behaviour documented via KDoc or dedicated docs when semantics evolve.
- For new configuration flags or environment expectations, note them in `config/` or module READMEs.

## Security & configuration
- Never hardcode API keys or tokens; samples rely on local `local.properties`/env vars.
- Sanitise logs: token payloads, JWTs, or user IDs should not leak to release logs.
- Be mindful of TLS/OkHttp settings—core is shared across products; changes ripple widely.

## PR & release hygiene
- Commits should be focused and imperative (e.g., `fix(socket): guard reconnect cleanup`).
- Before PR: run Spotless + relevant tests. Include test/task output in the PR description when significant.
- Coordinate with the release owner before modifying publishing metadata or version numbers.
- New APIs require documentation + unit tests; mention migration notes for behavioural changes.

### Quick checklist for agents
- [ ] Understand which module you’re editing and run its tests.
- [ ] Maintain explicit API boundaries; prefer additive changes.
- [ ] Ensure cleanup/teardown paths handle cancellation and failure (important for sockets, queues, retries).
- [ ] Keep concurrency deterministic—use structured coroutines and avoid global scope.
- [ ] Run Spotless (formatting) before finishing.
- [ ] Add tests for new APIs or behaviour changes.
- [ ] Coordinate with the release owner before modifying versioning metadata.
- [ ] Document new APIs and significant changes in `README.md` or module-specific docs.
- [ ] Sanitise logs to avoid leaking sensitive information.
