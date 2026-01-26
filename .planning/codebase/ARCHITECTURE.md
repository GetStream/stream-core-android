# Architecture

**Analysis Date:** 2026-01-26

## Pattern Overview

**Overall:** Layered client-server architecture with pluggable components for real-time connectivity.

**Key Characteristics:**
- Result-based error handling throughout (`Result<T>` instead of exceptions for expected failures)
- Coroutine-based concurrency with structured coroutine scopes
- Component lifecycle management via `StreamStartableComponent` interface
- Composition over inheritance - most behavior provided via injected components
- State Flow observables for reactive connection state and monitoring

## Layers

**API Layer (Public contracts):**
- Purpose: Stable interfaces exposed to product SDKs (Chat, Video, Feeds)
- Location: `stream-android-core/src/main/java/io/getstream/android/core/api/`
- Contains: Interfaces, data classes, enums for public consumption
- Depends on: Nothing - root dependencies
- Used by: Internal implementations, product SDKs

**Internal Implementation Layer:**
- Purpose: Concrete implementations of API contracts, not exposed outside core
- Location: `stream-android-core/src/main/java/io/getstream/android/core/internal/`
- Contains: Impl classes, session management, event routing
- Depends on: API layer, Android framework, OkHttp, Moshi, coroutines
- Used by: Only used internally and via API layer

**Model/Data Layer:**
- Purpose: Data classes and value objects for messages, events, configuration
- Location: `stream-android-core/src/main/java/io/getstream/android/core/api/model/` and `internal/model/`
- Contains: Event types, connection state, errors, user data
- Depends on: Nothing (clean data classes)
- Used by: All layers

**Socket/Network Layer:**
- Purpose: WebSocket management, HTTP interceptor chain, network/lifecycle monitoring
- Location: `stream-android-core/src/main/java/io/getstream/android/core/internal/socket/`, `internal/http/`
- Contains: Socket session, WebSocket wrapper, health monitor, interceptors
- Depends on: OkHttp, API models, serialization
- Used by: Client implementation

**Processing/Concurrency Layer:**
- Purpose: Backpressure management, deduplication, batching, retries
- Location: `stream-android-core/src/main/java/io/getstream/android/core/internal/processing/`
- Contains: Single-flight processor, serial queue, batcher, retry processor
- Depends on: Coroutines, logging
- Used by: Client, socket session, authentication

## Data Flow

**Connection Sequence:**

1. **User initiates connect** → `StreamClient.connect()` called
2. **Single-flight deduplication** → `StreamSingleFlightProcessor` ensures only one concurrent connection attempt
3. **Socket opening** → `StreamSocketSession.connect()` opens WebSocket via `StreamWebSocketFactory`
4. **State: Opening** → Connection state transitions to `Connecting.Opening(userId)`
5. **HTTP 101 response** → WebSocket handshake succeeds
6. **State: Authenticating** → Connection state transitions to `Connecting.Authenticating(userId)`
7. **Auth message sent** → `StreamWSAuthMessageRequest` with JWT from `StreamTokenManager` sent to server
8. **Success response** → Server responds with `StreamClientConnectedEvent` (type: "connection.ok")
9. **State: Connected** → Connection state transitions to `Connected(connectedUser, connectionId)`
10. **Lifecycle observers start** → Health monitor begins periodic checks, socket session begins listening for events

**Event Processing Pipeline:**

1. **Socket receives message** → `StreamWebSocketListener.onMessage(text)`
2. **Batcher queues** → Message offered to `StreamBatcher` for buffering/coalescing
3. **Batch flush** → When batch full (10 items) or timeout (100ms-1s adaptive), messages flushed
4. **Event parser** → `StreamCompositeEventSerializationImpl` routes by "type" field to core/product deserializers
5. **Core event handling** → Internal events (connection.ok, health.check) processed locally
6. **Product event delivery** → Non-core events delivered via `subscriptionManager.forEach { it.onEvent(...) }`
7. **Client listener dispatch** → All registered `StreamClientListener` instances notified

**Reconnection Flow:**

1. **State trigger** → Network lost OR app backgrounded OR health check timeout
2. **Recovery evaluation** → `StreamConnectionRecoveryEvaluator` checks if reconnect should occur
3. **Automatic reconnect** → If connected AND (network recovered OR app resumed), connect again
4. **Watch reestablishment** → `StreamWatcher<T>` notifies all rewatch listeners with full registry
5. **Product re-watches** → Product SDKs re-establish server-side watches via callback

**Token Refresh Flow:**

1. **Token needed** → `StreamAuthInterceptor` detects missing/expired token
2. **Load or refresh** → `StreamTokenManager` fetches from `TokenProvider` or refreshes existing
3. **Single-flight dedup** → Multiple concurrent requests share same token refresh via single-flight processor
4. **Retry with new token** → HTTP request retried with new Authorization header
5. **Token error codes** → Codes 40 (invalid), 41 (expired), 42 (revoked) trigger refresh

**State Management:**

- **Connection state**: `MutableStateFlow<StreamConnectionState>` - single source of truth
- **Network/Lifecycle state**: Observed separately, aggregated for recovery decisions
- **Health check**: 25-second intervals with 60-second timeout for liveness detection
- **Events flow**: Via `subscriptionManager` to all registered `StreamClientListener` instances

## Key Abstractions

**StreamClient (Facade):**
- Purpose: Main entry point for connection lifecycle
- Examples: `stream-android-core/src/main/java/io/getstream/android/core/api/StreamClient.kt`
- Pattern: Factory function with dependency injection, wraps internal `StreamClientImpl`
- Responsibilities: Expose `connect()`, `disconnect()`, `connectionState` StateFlow

**StreamSocketSession (Coordinator):**
- Purpose: Manages WebSocket lifecycle, authentication, event batching and routing
- Examples: `stream-android-core/src/main/java/io/getstream/android/core/internal/socket/StreamSocketSession.kt`
- Pattern: Holds references to socket, health monitor, batcher, event parser
- Responsibilities: Socket lifecycle, handshake flow, event distribution

**StreamSingleFlightProcessor (Deduplication):**
- Purpose: Prevents concurrent execution of same operation, deduplicates results
- Examples: `stream-android-core/src/main/java/io/getstream/android/core/internal/processing/StreamSingleFlightProcessorImpl.kt`
- Pattern: `ConcurrentHashMap<Key, Deferred<Result<T>>>` - multiple callers await same result
- Responsibilities: Deduplicate token refreshes, connection attempts

**StreamBatcher (Coalescing):**
- Purpose: Buffer and batch high-frequency messages with adaptive window
- Examples: `stream-android-core/src/main/java/io/getstream/android/core/internal/processing/StreamBatcherImpl.kt`
- Pattern: Dedicated worker coroutine, channel-based queue with configurable size/timeout
- Responsibilities: Accumulate messages, flush on size/timeout, double window if full

**StreamSerialProcessingQueue (Ordered execution):**
- Purpose: Execute suspend functions sequentially with backpressure support
- Examples: `stream-android-core/src/main/java/io/getstream/android/core/internal/processing/StreamSerialProcessingQueueImpl.kt`
- Pattern: Dedicated worker coroutine, inbox channel with CompletableDeferred replies
- Responsibilities: FIFO execution, suspension on full queue, caller awaits completion

**StreamTokenManager (Auth lifecycle):**
- Purpose: Manage JWT tokens, cache, refresh on expiration, deduplicate concurrent refreshes
- Examples: `stream-android-core/src/main/java/io/getstream/android/core/internal/authentication/StreamTokenManagerImpl.kt`
- Pattern: Wraps `TokenProvider`, uses single-flight processor for deduplication
- Responsibilities: Load initial token, refresh on error, cache result

**StreamCompositeEventSerializationImpl (Event routing):**
- Purpose: Deserialize WebSocket events and route to appropriate handlers
- Examples: `stream-android-core/src/main/java/io/getstream/android/core/internal/serialization/StreamCompositeEventSerializationImpl.kt`
- Pattern: Peeks "type" field, dispatches to core or product deserializers
- Responsibilities: Parse JSON, route by type, return composite result with both core/product events

**StreamWatcher<T> (Watch registry):**
- Purpose: Maintain list of watched resources, trigger re-watches on reconnection
- Examples: `stream-android-core/src/main/java/io/getstream/android/core/api/watcher/StreamWatcher.kt`, `internal/watcher/StreamWatcherImpl.kt`
- Pattern: Generic type parameter allows watching any identifier type (String, custom classes)
- Responsibilities: Add/remove watch entries, notify listeners on connection changes, observe connection state

**StreamSubscriptionManager<T> (Listener registry):**
- Purpose: Store listeners with multiple retention policies (strong/weak references)
- Examples: `stream-android-core/src/main/java/io/getstream/android/core/api/subscribe/StreamSubscriptionManager.kt`
- Pattern: `ConcurrentLinkedQueue` with optional weak reference support
- Responsibilities: Add/remove listeners, broadcast events, garbage-collect weakly-held listeners

## Entry Points

**StreamClient Factory Function:**
- Location: `stream-android-core/src/main/java/io/getstream/android/core/api/StreamClient.kt:210-385`
- Triggers: Called by product SDKs to create client instance
- Responsibilities: Wire all dependencies (socket, monitors, processors, serialization), create `StreamClientImpl`

**StreamSocketSession.connect():**
- Location: `stream-android-core/src/main/java/io/getstream/android/core/internal/socket/StreamSocketSession.kt:182-462` (from CLAUDE.md)
- Triggers: Called by `StreamClientImpl.connect()` after single-flight check
- Responsibilities: Open WebSocket, send auth message, handle auth response, start health monitor

**Socket Message Listener:**
- Location: `stream-android-core/src/main/java/io/getstream/android/core/internal/socket/StreamSocketSession.kt:82-120`
- Triggers: On every WebSocket message received
- Responsibilities: Acknowledge health check, batch message, detect failures

**Health Monitor:**
- Location: `stream-android-core/src/main/java/io/getstream/android/core/internal/socket/monitor/StreamHealthMonitorImpl.kt`
- Triggers: Periodic (25s interval) after connection established
- Responsibilities: Send health check requests, detect timeout after 60s without ack

**Network/Lifecycle Monitors:**
- Location: `stream-android-core/src/main/java/io/getstream/android/core/internal/observers/`
- Triggers: On connectivity change or app lifecycle state change
- Responsibilities: Notify recovery evaluator of state changes

## Error Handling

**Strategy:** Result-based error handling with explicit token error codes

**Patterns:**

1. **Expected failures** → Return `Result<T>` (failure branch contains error)
   - Location: All public API methods
   - Example: `client.connect()` returns `Result<StreamConnectedUser>`

2. **Token errors** → Detect via error code (40, 41, 42) not HTTP status
   - Location: `StreamAuthInterceptor.kt`, `StreamClientImpl.kt`
   - Pattern: `onTokenError` extension method checks `StreamEndpointErrorData.code`

3. **Socket errors** → Detected via listener callbacks (onFailure, onClosed)
   - Location: `StreamSocketSession.kt:100-119`
   - Pattern: Emit `Disconnected` state, cleanup resources

4. **Batcher overflow** → Message drop with error callback
   - Location: `StreamSocketSession.kt:88-94`
   - Pattern: Call `disconnect(error)` to propagate failure

5. **Health monitor timeout** → Disconnect with exception
   - Location: `StreamHealthMonitorImpl.kt`
   - Pattern: Emit `Disconnected` state with timeout exception

## Cross-Cutting Concerns

**Logging:** Lambda-based via `StreamLogger`
- Strategy: Avoid expensive computations via lazy evaluation
- Pattern: `logger.d { "message" }` only evaluated if DEBUG enabled
- Location: Used throughout all components

**Validation:** Input validation on public API boundaries
- Strategy: Validate user data on `connect()`, reject invalid tokens
- Pattern: Return `Result.failure` on invalid input

**Authentication:** JWT-based via provider pattern
- Strategy: Delegate token fetch to product SDK, cache locally
- Pattern: `TokenProvider.getToken()` called on demand, single-flight deduplicated

**Thread safety:** All public APIs are thread-safe
- Strategy: Atomic flags, `ConcurrentHashMap`, coroutine-safe state flows
- Pattern: No external synchronization needed for public method calls

**Resource cleanup:** Proper disposal of sockets, monitors, subscriptions
- Strategy: Idempotent cleanup guards via `AtomicBoolean`
- Pattern: `disconnect()` is idempotent, subsequent calls are no-ops for listeners

---

*Architecture analysis: 2026-01-26*
