# Stream Android Core

## What This Is

Internal foundational library that powers all of Stream's Android SDKs (Chat, Video, Feeds). Provides shared infrastructure for real-time WebSocket connectivity, JWT authentication, connection state management, and reliability patterns. Not for public consumption — used only by Stream product SDKs.

## Core Value

Reliable real-time connectivity that automatically recovers from network disruptions and app lifecycle changes.

## Requirements

### Validated

- ✓ WebSocket connection with JWT authentication — existing
- ✓ Connection state machine (Idle → Opening → Authenticating → Connected → Disconnected) — existing
- ✓ Automatic reconnection on network recovery and app resume — existing
- ✓ Token management with single-flight refresh deduplication — existing
- ✓ Health monitoring with configurable heartbeat (25s) and liveness timeout (60s) — existing
- ✓ Event batching with adaptive window (100ms-1s) — existing
- ✓ HTTP interceptor chain (API key, auth, client info, connection ID, error parsing) — existing
- ✓ Network and lifecycle monitoring via Android system callbacks — existing
- ✓ Watch registry with automatic re-watch on reconnection — existing
- ✓ Result-based error handling throughout — existing
- ✓ Thread-safe APIs with coroutine-based concurrency — existing
- ✓ Moshi JSON serialization with KSP code generation — existing
- ✓ Custom lint rules for API consistency — existing

### Active

(None yet — ready for next milestone)

### Out of Scope

- Direct database/persistence integration — product SDKs handle their own storage
- UI components — this is infrastructure only
- Public API for end consumers — internal to Stream SDKs

## Context

**Purpose:** Shared foundation across Chat, Video, and Feeds Android SDKs. Changes here affect all product SDKs.

**Architecture:** Layered client-server with pluggable components. API layer exposes stable contracts; internal layer implements them. Processing layer handles concurrency patterns (single-flight, batching, serial queues).

**Visibility annotations:**
- `@StreamPublishedApi` — Stable, SDK-exposed (breaking changes require major version bump)
- `@StreamInternalApi` — Internal infrastructure, may change without notice
- `@StreamDelicateApi` — Advanced APIs requiring careful use

**Current version:** 2.0.0

## Constraints

- **Tech stack**: Kotlin 2.2.0, Android minSdk 21, OkHttp 5.x, Moshi 1.15.2
- **Explicit API mode**: All public declarations must have visibility modifiers
- **Thread model**: OkHttp interceptors are synchronous; uses `runBlocking` for token operations
- **Git workflow**: Feature branches with PRs to `develop`; never commit directly to `develop`

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Result<T> over exceptions | Explicit error handling, forces callers to handle failures | ✓ Good |
| Single-flight for token refresh | Prevents concurrent refresh storms, deduplicates network calls | ✓ Good |
| Adaptive batching window | Balances latency vs throughput based on traffic patterns | ✓ Good |
| StateFlow for connection state | Hot observable, immediate state for new collectors | ✓ Good |
| runBlocking in interceptors | OkHttp constraint — interceptors are synchronous | ⚠️ Revisit (see CONCERNS.md) |

---
*Last updated: 2026-01-27 after initialization*
