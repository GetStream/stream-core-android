# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Stream Android Core is an **internal foundational library** that powers all of Stream's Android SDKs (Chat, Video, Feeds). It provides shared infrastructure for real-time connectivity, authentication, state management, and reliability.

**Key characteristics:**
- Not for public consumption - used only by Stream product SDKs
- Uses explicit API mode (`kotlin { explicitApi() }`)
- Three visibility levels: `@StreamPublishedApi` (stable, SDK-exposed), `@StreamInternalApi` (may change), `@StreamDelicateApi` (advanced/dangerous)
- Result-based error handling throughout (`Result<T>`)
- Thread-safe APIs with coroutine-based concurrency
- Moshi for JSON serialization with KSP code generation

## Build Commands

### Testing
```bash
# Run all unit tests for core library
./gradlew :stream-android-core:test

# Run tests with coverage report
./gradlew koverHtmlReportCoverage
# Coverage report: stream-android-core/build/reports/kover/htmlCoverage/index.html

# Run a specific test class
./gradlew :stream-android-core:test --tests "io.getstream.android.core.internal.processing.StreamRetryProcessorImplTest"

# Run a specific test method
./gradlew :stream-android-core:test --tests "io.getstream.android.core.internal.processing.StreamRetryProcessorImplTest.testExponentialBackoff"

# Run instrumented tests on connected device
./gradlew :stream-android-core:connectedDebugAndroidTest
```

### Building
```bash
# Build the library
./gradlew :stream-android-core:build

# Build all modules
./gradlew build

# Assemble release AAR
./gradlew :stream-android-core:assembleRelease
# Output: stream-android-core/build/outputs/aar/stream-android-core-release.aar

# Clean build artifacts
./gradlew clean
```

### Code Quality
```bash
# Run detekt (static analysis) - auto-corrects issues
./gradlew detekt

# Run spotless (code formatting with ktfmt)
./gradlew spotlessApply

# Run all checks (tests + lint + detekt)
./gradlew check

# Run lint
./gradlew :stream-android-core:lint
```

### Publishing
```bash
# Print all artifacts that will be published
./gradlew printAllArtifacts

# Publish to Maven Central (requires credentials)
./gradlew publish
```

## Architecture Overview

### Core Entry Point: StreamClient

`StreamClient` is the main interface for establishing connections to Stream services. Factory function location: `stream-android-core/src/main/java/io/getstream/android/core/api/StreamClient.kt`

Implementation: `stream-android-core/src/main/java/io/getstream/android/core/internal/StreamClientImpl.kt`

**Key operations:**
- `suspend fun connect(data: ConnectUserData): Result<StreamConnectionState.Connected>` - Opens socket, authenticates, starts monitoring
- `suspend fun disconnect(): Result<Unit>` - Closes socket, stops monitoring, cleans up
- `val connectionState: StateFlow<StreamConnectionState>` - Observable connection state
- Uses single-flight processor to prevent concurrent connect/disconnect

### Connection State Machine

Location: `stream-android-core/src/main/java/io/getstream/android/core/api/model/connection/StreamConnectionState.kt`

**States:**
```kotlin
sealed class StreamConnectionState {
    object Idle  // Initial state, no connection attempt

    sealed class Connecting {
        data class Opening(userId: String)         // Socket opening
        data class Authenticating(userId: String)  // Sending auth request
    }

    data class Connected(
        user: StreamConnectedUser,
        connectionId: String
    )

    data class Disconnected(cause: Throwable? = null)
}
```

**State Transitions:**

1. **Idle → Connecting.Opening**
   - Trigger: User calls `streamClient.connect(data)`
   - Location: `StreamClientImpl.kt:75-157`, `StreamSocketSession.kt:311`
   - Action: Opens WebSocket with HTTP upgrade request

2. **Opening → Authenticating**
   - Trigger: Socket receives HTTP 101 response
   - Location: `StreamSocketSession.kt:324-366`
   - Action: Sends `StreamWSAuthMessageRequest` with JWT token and user details

3. **Authenticating → Connected**
   - Trigger: Receives `StreamClientConnectedEvent` (type: "connection.ok")
   - Location: `StreamSocketSession.kt:376-398`
   - Action: Starts health monitor, emits Connected state

4. **Connected → Disconnected**
   - Triggers:
     - Network lost: `StreamNetworkMonitor` detects connectivity loss
     - App backgrounded: `StreamLifecycleMonitor` detects background state
     - Health check timeout: No events received within liveness threshold (60s)
     - Explicit disconnect: User calls `streamClient.disconnect()`
     - Socket error: WebSocket failure/close callback
   - Location: `StreamSocketSession.kt:100-119`, `StreamConnectionRecoveryEvaluatorImpl.kt:68-70`

5. **Any → Disconnected**
   - Trigger: Socket failure callback (`onFailure`, `onClosed`)
   - Location: `StreamSocketSession.kt:100-119`

**Recovery Logic:**
- `StreamConnectionRecoveryEvaluatorImpl.kt:44-99` evaluates whether to auto-reconnect
- Reconnects if: previously connected AND (network became available OR app returned to foreground with network)
- Disconnects if: connected/connecting AND (network unavailable OR app backgrounded)

### Component Hierarchy

```
StreamClient (Main Interface)
├── StreamSocketSession (WebSocket coordinator)
│   ├── StreamWebSocket (OkHttp WebSocket wrapper)
│   ├── StreamHealthMonitor (Heartbeat: 25s interval, 60s timeout)
│   └── StreamBatcher (10 items, 100ms initial, 1s max delay)
├── StreamTokenManager (Auth token lifecycle)
│   ├── StreamTokenProvider (User-implemented token fetcher)
│   └── StreamSingleFlightProcessor (Token refresh deduplication)
├── StreamNetworkAndLifeCycleMonitor (Combined state)
│   ├── StreamNetworkMonitor (ConnectivityManager callbacks)
│   └── StreamLifecycleMonitor (ProcessLifecycleOwner observer)
├── StreamConnectionRecoveryEvaluator (Reconnect heuristics)
├── StreamCidWatcher (Watch registry & rewatch coordinator)
│   ├── StreamSubscriptionManager<StreamCidRewatchListener> (Rewatch listener registry)
│   └── StreamSubscriptionManager<StreamClientListener> (Connection state monitoring)
└── StreamSubscriptionManager<StreamClientListener> (Event distribution)
```

### Watch Management: StreamCidWatcher

**Purpose:** Manages a registry of watched resources (channels/conversations) and automatically triggers re-watching when the WebSocket connection state changes.

**Location:**
- Interface: `stream-android-core/src/main/java/io/getstream/android/core/api/watcher/StreamCidWatcher.kt`
- Implementation: `stream-android-core/src/main/java/io/getstream/android/core/internal/watcher/StreamCidWatcherImpl.kt`
- Listener: `stream-android-core/src/main/java/io/getstream/android/core/api/watcher/StreamCidRewatchListener.kt`

**Key Concept:** When the WebSocket reconnects (network recovery, app resume), all active watches must be re-established on the server. `StreamCidWatcher` maintains which `StreamCid`s (Channel IDs) are currently watched and notifies listeners on every `Connected` state transition.

**Core Operations:**
```kotlin
// Add a CID to watch registry
fun watch(cid: StreamCid): Result<StreamCid>

// Remove a CID from watch registry
fun stopWatching(cid: StreamCid): Result<StreamCid>

// Clear all watched CIDs
fun clear(): Result<Unit>

// Subscribe to rewatch notifications
fun subscribe(
    listener: StreamCidRewatchListener,
    options: StreamSubscriptionManager.Options
): Result<StreamSubscription>

// Lifecycle management (StreamStartableComponent)
fun start(): Result<Unit>
fun stop(): Result<Unit>
```

**Usage Flow:**
1. Product SDK watches a channel: `watcher.watch(StreamCid.parse("messaging:general"))`
2. Watcher adds CID to internal `ConcurrentHashMap<StreamCid, Unit>` registry
3. Product SDK registers a rewatch listener: `watcher.subscribe(StreamCidRewatchListener { cids, connectionId -> ... })`
4. Call `watcher.start()` to begin monitoring connection state changes
5. On `StreamConnectionState.Connected` event, watcher invokes all listeners with complete CID list AND the current connectionId
6. Product SDK re-establishes server-side watches for each CID using the provided connectionId

**Implementation Details:**
- Thread-safe: Uses `ConcurrentHashMap` for CID registry (line 52 in `StreamCidWatcherImpl.kt`)
- Async execution: Rewatch callbacks invoked on internal coroutine scope with `SupervisorJob + Dispatchers.Default` (line 61)
- Error handling: Exceptions from rewatch callbacks are caught, logged, and surfaced via `StreamClientListener.onError` (lines 77-82)
- Idempotent: Multiple `watch()` calls with same CID only maintain one entry
- Only triggers on `Connected` state when registry is non-empty (line 67)
- Connection ID extracted from `Connected` state and passed to all listeners (line 70)

**Test Coverage:**
- Location: `stream-android-core/src/test/java/io/getstream/android/core/internal/watcher/StreamCidWatcherImplTest.kt`
- 29 comprehensive test cases covering watch operations, lifecycle, state changes, error handling, concurrency, and connectionId verification
- 100% instruction/branch/line coverage (verified via Kover)

## Configuration Defaults

### StreamBatcher
Location: `stream-android-core/src/main/java/io/getstream/android/core/internal/processing/StreamBatcherImpl.kt:160-173`

```kotlin
batchSize: Int = 10              // Max items before forced flush
initialDelayMs: Long = 100       // Initial debounce window
maxDelayMs: Long = 1_000         // Maximum debounce window
autoStart: Boolean = true        // Auto-start on first item
channelCapacity: Int = UNLIMITED // Buffer capacity
```

**Adaptive window:** Doubles delay if batch was full, resets to initial if not (lines 120-125)

### StreamHealthMonitor
Location: `stream-android-core/src/main/java/io/getstream/android/core/internal/socket/monitor/StreamHealthMonitorImpl.kt:39-49`

```kotlin
interval: Long = 25_000          // Send health check every 25 seconds
livenessThreshold: Long = 60_000 // Mark unhealthy after 60s without ack
```

Health check event: `StreamClientConnectedEvent` echoed back to server (line 212-227 in `StreamSocketSession.kt`)

### StreamRetryPolicy
Location: `stream-android-core/src/main/java/io/getstream/android/core/api/model/retry/StreamRetryPolicy.kt`

**Exponential backoff (lines 71-92):**
```kotlin
minRetries: Int = 1
maxRetries: Int = 5
backoffStepMillis: Long = 250      // 0ms → 250ms → 500ms → 1000ms...
maxBackoffMillis: Long = 15_000    // Cap at 15 seconds
initialDelayMillis: Long = 0
giveUpFunction: (attempt, error) -> Boolean = { _, _ -> false }
```

**Linear backoff (lines 111-130):**
```kotlin
minRetries: Int = 1
maxRetries: Int = 5
initialDelayMillis: Long = 1_000   // 1s → 2s → 3s → 4s...
maxDelayMillis: Long = 30_000      // Cap at 30 seconds
```

### StreamSocketConfig
Location: `stream-android-core/src/main/java/io/getstream/android/core/api/model/config/StreamSocketConfig.kt:31-98`

```kotlin
url: String                        // WebSocket URL (wss://...)
apiKey: StreamApiKey               // API authentication key
authType: String                   // "jwt" or "anonymous"
clientInfoHeader: StreamHttpClientInfoHeader  // X-Stream-Client header
```

### HTTP Configuration
**OkHttp interceptors** (automatically added if `automaticInterceptors = true`):
- `StreamApiKeyInterceptor` - Adds `?api_key=<key>` query parameter
- `StreamAuthInterceptor` - Adds Authorization header, handles token refresh
- `StreamClientInfoInterceptor` - Adds `X-Stream-Client` header
- `StreamConnectionIdInterceptor` - Adds `?connection_id=<id>` if available
- `StreamEndpointErrorInterceptor` - Parses error responses into exceptions

## WebSocket Authentication Flow

**Step-by-step (StreamSocketSession.connect() lines 182-462):**

1. **Register listeners** (lines 314-454)
   - Permanent event listener for ongoing messages
   - Temporary handshake listener for auth response

2. **Emit Opening state** (line 311)
   ```kotlin
   notifyState(StreamConnectionState.Connecting.Opening(userId))
   ```

3. **Open WebSocket** (line 457)
   - Factory creates HTTP upgrade request: `StreamWebSocketFactoryImpl.kt:36-54`
   - Headers included:
     - `?api_key=<key>`
     - `stream-auth-type: jwt`
     - `X-Stream-Client: stream-android-core/<version>/<platform>`

4. **Receive HTTP 101 response** (lines 324-366)
   - Emit Authenticating state
   - Build `StreamWSAuthMessageRequest`:
     ```kotlin
     StreamWSAuthMessageRequest(
         products = ["chat", "messaging"],  // Product list
         token = "<JWT>",                    // From TokenManager
         userDetails = StreamConnectUserDetailsRequest(
             id, name, image, language,
             invisible, custom
         )
     )
     ```
   - Serialize and send via WebSocket (lines 345-356)

5. **Receive authentication response** (lines 369-415)
   - Success: `StreamClientConnectedEvent` (type: "connection.ok")
     - Extract `connectionId` and `me` (user data)
     - Store event for health checks (line 397)
     - Start health monitor (line 288)
     - Emit Connected state (line 289)
     - Resume coroutine with success (line 291)

   - Failure: `StreamClientConnectionErrorEvent` (type: "connection.error")
     - Contains `StreamEndpointErrorData` with error details
     - Emit Disconnected state
     - Resume coroutine with failure (line 405)

6. **Token refresh on auth failure** (lines 180-201 in `StreamClientImpl.kt`)
   ```kotlin
   val response = socketSession.connect(data)
       .onTokenError { error, code ->
           tokenManager.invalidate()
           val refreshed = tokenManager.refresh().getOrThrow()
           socketSession.connect(data.copy(token = refreshed.rawValue))
       }
   ```

**Token error codes:**
- `40` - Token signature invalid
- `41` - Token expired
- `42` - Token revoked

These codes come from `StreamEndpointErrorData.code`, not HTTP status codes. Check location: `StreamAuthInterceptor.kt:119`

## Error Types & Handling

### Core Error Types

**StreamEndpointErrorData** (API errors from backend):
```kotlin
code: Int                    // Error code (40, 41, 42 for token errors)
duration: String?            // Request processing time
message: String?             // Human-readable message
moreInfo: String?            // Documentation URL
statusCode: Int?             // HTTP status code
details: List<Int>?          // Additional error detail codes
unrecoverable: Boolean?      // Cannot retry flag
exceptionFields: Map<String, String>?  // Extra context
```

**StreamEndpointException** - Wraps API errors:
```kotlin
message: String
cause: Throwable?
apiError: StreamEndpointErrorData?
```

**StreamClientException** - Client-level errors:
- Socket health failure: `"Socket did not receive any events."` (line 231 in `StreamSocketSession.kt`)
- Message drop: `"Failed to offer message to debounce processor"` (line 90-91)

**IOException** - Socket closure:
```kotlin
IOException("Socket closed. Code: $code, Reason: $reason")
```

### Error Handling Patterns

**1. onTokenError Extension** (`StreamClientImpl.kt:193-200`):
```kotlin
suspend fun <T> Result<T>.onTokenError(
    handler: suspend (error: StreamEndpointErrorData, code: Int) -> Result<T>
): Result<T> = fold(
    onSuccess = { Result.success(it) },
    onFailure { error ->
        if (error is StreamEndpointException && error.apiError?.code in listOf(40, 41, 42)) {
            handler(error.apiError!!, error.apiError!!.code)
        } else {
            Result.failure(error)
        }
    }
)
```

**2. HTTP Interceptor Token Refresh** (`StreamAuthInterceptor.kt:65-111`):
```kotlin
override fun intercept(chain: Interceptor.Chain): Response {
    val token = runBlocking { tokenManager.loadIfAbsent() }
    val authed = original.withAuthHeaders(authType, token.rawValue)
    val first = chain.proceed(authed)

    // Check for token error
    if (!first.isSuccessful && isTokenInvalidErrorCode(errorCode)) {
        tokenManager.invalidate()
        val refreshed = runBlocking { tokenManager.refresh() }
        return chain.proceed(retried)  // Single retry
    }

    return first
}
```

**Note:** Uses `runBlocking` because OkHttp interceptors are synchronous.

**3. Recovery Evaluator** (`StreamConnectionRecoveryEvaluatorImpl.kt:44-99`):
```kotlin
fun evaluate(snapshot: StateSnapshot): Recovery? = when {
    shouldConnect(snapshot) -> Recovery.Connect(snapshot)
    shouldDisconnect(snapshot) -> Recovery.Disconnect(reason)
    else -> null  // No action needed
}
```

## HTTP Interceptor Chain

**Request Order** (outgoing):
```
Request
  ↓
1. StreamApiKeyInterceptor        // Add ?api_key=<key>
  ↓
2. StreamAuthInterceptor          // Add Authorization: <token>
                                  // Handle token refresh on 401/403
  ↓
3. StreamClientInfoInterceptor    // Add X-Stream-Client: <version>
  ↓
4. StreamConnectionIdInterceptor  // Add ?connection_id=<id>
  ↓
5. StreamEndpointErrorInterceptor // Parse error responses
  ↓
Network
  ↓
5. StreamEndpointErrorInterceptor // Throw StreamEndpointException if error
  ↓
Response
```

**Key Implementation Details:**

- **StreamAuthInterceptor**: Synchronous; uses `runBlocking` for token operations (lines 65-111)
- **Token refresh**: Automatic single retry with refreshed token on codes 40, 41, 42 (not 401/403 HTTP status)
- **Error parsing**: `StreamEndpointErrorInterceptor` peeks response body without consuming (non-intrusive)

## Threading & Concurrency Guarantees

| Component | Thread Model | Mechanism | Notes |
|-----------|-------------|-----------|-------|
| **StreamClientImpl** | Coroutine-based | Launched on provided scope | Recommend `CoroutineScope(SupervisorJob() + Dispatchers.IO)` |
| **StreamSocketSession** | Callback thread | OkHttp WebSocket listener | Callbacks run on OkHttp's thread pool |
| **StreamBatcherImpl** | Single worker | Dedicated `Job` | Sequential batch processing (lines 99-131) |
| **StreamSerialProcessingQueueImpl** | Single worker | Dedicated `Job` + `Mutex` | FIFO execution, thread-safe submission (lines 60-121) |
| **StreamSingleFlightProcessor** | Caller's thread | `ConcurrentHashMap` + `async(LAZY)` | Multiple callers await same deferred (lines 41-82) |
| **StreamAuthInterceptor** | OkHttp thread | `runBlocking` for suspend ops | Blocks HTTP thread during token operations |
| **TokenManager** | Coroutine-safe | `StateFlow` + SingleFlight | Only one refresh in flight at a time |
| **ConnectionIdHolder** | Lock-free | `AtomicReference` | Thread-safe read/write |
| **HealthMonitor** | Worker coroutine | `scope.launch` + `delay` | Periodic checks on scope's dispatcher (lines 71-92) |
| **SubscriptionManager** | Thread-safe | `ConcurrentLinkedQueue` + GC | Supports concurrent subscribe/unsubscribe/notify |

**Key Concurrency Patterns:**

1. **Single-Flight Deduplication** (`StreamSingleFlightProcessorImpl.kt:41-82`):
   ```kotlin
   override suspend fun <T> run(key: StreamTypedKey<T>, block: suspend () -> T): Result<T> {
       // Fast path: reuse existing
       flights[key]?.let { return it.await() }

       // Slow path: create new async(LAZY)
       val newExecution = scope.async(start = LAZY) { runCatching { block() } }
       val existing = flights.putIfAbsent(key, newExecution)
       val job = existing ?: newExecution.also { it.start() }

       return job.await()
   }
   ```
   - **Guarantee:** Only first caller executes; others await same result
   - **Cancellation:** Cancelling one awaiter doesn't cancel others (result cached)
   - **Cleanup:** Entry removed in `finally` block only if same job still mapped

2. **Serial Queue Backpressure** (`StreamSerialProcessingQueueImpl.kt:60-97`):
   ```kotlin
   override suspend fun <T> submit(job: suspend () -> T): Result<T> {
       val reply = CompletableDeferred<Result<T>>()
       inbox.send(JobItem(block = job, reply = reply))  // Suspends if full
       return reply.await()
   }
   ```
   - **Guarantee:** Jobs execute in submission order
   - **Worker loop:** Single coroutine processes inbox sequentially
   - **Cancellation:** Job-level cancellation doesn't stop queue

3. **Atomic State Guards** (`StreamSocketSession.kt:68-71`):
   ```kotlin
   private val closingByUs = AtomicBoolean(false)
   private val cleaned = AtomicBoolean(false)
   ```
   - Prevents duplicate disconnect notifications
   - Idempotent cleanup via `compareAndSet` (line 465)

## Event Serialization & Deserialization

### Composite Pattern

Location: `stream-android-core/src/main/java/io/getstream/android/core/internal/serialization/StreamCompositeEventSerializationImpl.kt`

**Purpose:** Route events to appropriate deserializers based on "type" field.

**Event Container:**
```kotlin
class StreamCompositeSerializationEvent<T>(
    val core: StreamClientWsEvent? = null,     // Internal events
    val product: T? = null                      // Product-specific events
)
```

**Deserialization Process** (lines 98-125):

1. **Peek "type" field** (lines 127-156):
   ```kotlin
   private fun peekType(raw: String): String? {
       val reader = JsonReader.of(Buffer().writeUtf8(raw))
       // Parse JSON, find "type" field, return value
   }
   ```

2. **Route by type**:
   ```kotlin
   when (type) {
       in alsoExternal -> {
           // Parse as BOTH core and product
           core = internal.deserialize(raw)
           product = external.deserialize(raw)
           return both(core, product)
       }
       in internalTypes -> {
           // Parse as CORE only ("connection.ok", "connection.error", "health.check")
           core = internal.deserialize(raw)
           return internal(core)
       }
       else -> {
           // Parse as PRODUCT only (Chat, Video, Feeds events)
           product = external.deserialize(raw)
           return external(product)
       }
   }
   ```

3. **Notify listeners** (`StreamSocketSession.kt:256-262`):
   ```kotlin
   subscriptionManager.forEach { listener ->
       coreEvent?.takeUnless { it is StreamHealthCheckEvent }?.let {
           listener.onEvent(it)
       }
       productEvent?.let { listener.onEvent(it) }
   }
   ```

### Core Event Types

**StreamClientConnectedEvent** (type: "connection.ok"):
```kotlin
connectionId: String
me: StreamConnectedUser  // Full user profile
type: "connection.ok"
```

**StreamClientConnectionErrorEvent** (type: "connection.error"):
```kotlin
connectionId: String
createdAt: Date
error: StreamEndpointErrorData
type: "connection.error"
```

**StreamHealthCheckEvent** (type: "health.check"):
- Echoes back `StreamClientConnectedEvent` every 25 seconds
- Filtered from user event callbacks (line 258 in `StreamSocketSession.kt`)

### Authentication Message

**StreamWSAuthMessageRequest**:
```kotlin
products: List<String>  // ["chat", "messaging", "video"]
token: String           // JWT token from TokenManager
userDetails: StreamConnectUserDetailsRequest {
    id: String
    name: String?
    image: String?
    invisible: Boolean = false
    language: String?
    custom: Map<String, Any?>?
}
```

## Important Patterns & Conventions

### 1. Result-Based Error Handling
All APIs return `Result<T>` instead of throwing exceptions:
```kotlin
component.start()
    .onSuccess { /* started */ }
    .onFailure { error -> logger.e(error) { "Failed to start" } }
```
**Exception:** `CancellationException` is rethrown (not captured in Result).

### 2. Component Lifecycle
Most components implement `StreamStartableComponent`:
```kotlin
interface StreamStartableComponent {
    fun start(): Result<Unit>
    fun stop(): Result<Unit>
}
```
**Always:** Call `start()` before use, `stop()` when done. Components don't work until started.

### 3. State Flow for Observability
Hot state flows provide immediate state snapshots to new collectors:
```kotlin
streamClient.connectionState.collect { state ->
    when (state) {
        is StreamConnectionState.Idle -> {}
        is StreamConnectionState.Connecting.Opening -> {}
        is StreamConnectionState.Connecting.Authenticating -> {}
        is StreamConnectionState.Connected -> { /* use connectionId */ }
        is StreamConnectionState.Disconnected -> { /* check cause */ }
    }
}
```

### 4. Threading & Main Thread Requirements
- All public APIs are thread-safe
- Android Lifecycle operations **must** run on main thread - library uses `runOnMainLooper` internally
- Suspend functions don't block threads
- Use `SupervisorJob` for structured concurrency (failures don't cancel siblings)

### 5. Dependency Injection via Factory Functions
Prefer factory functions over direct instantiation:
```kotlin
// ✅ Factory function (stable across implementation changes)
val queue = StreamSerialProcessingQueue(logger, scope)

// ❌ Direct instantiation (couples to implementation)
val queue = StreamSerialProcessingQueueImpl(logger, scope)
```

### 6. Subscription Management Memory Model
```kotlin
// For UI components (auto-cleanup when GC'd)
subscriptionManager.subscribe(
    listener = this,
    options = Options(retention = AUTO_REMOVE)
)

// For services/singletons (explicit lifecycle)
val subscription = subscriptionManager.subscribe(
    listener = this,
    options = Options(retention = KEEP_UNTIL_CANCELLED)
).getOrThrow()

// Always cancel when done
subscription.cancel()
```

### 7. Idempotent Operations
Use atomic guards for idempotency:
```kotlin
private val cleaned = AtomicBoolean(false)

private fun cleanup() {
    if (!cleaned.compareAndSet(false, true)) {
        return  // Already cleaned
    }
    // Perform cleanup
}
```

### 8. Adaptive Backoff in Batcher
Window doubles if batch was full (high traffic), resets if not (low traffic):
```kotlin
// StreamBatcherImpl.kt:120-125
val isFull = buffer.size >= batchSize
windowMs = if (isFull) {
    (windowMs * 2).coerceAtMost(maxDelayMs)
} else {
    initialDelayMs
}
```

## Testing Conventions

### Unit Tests
- Located in `stream-android-core/src/test/java/`
- Use JUnit 4, MockK for mocking
- Robolectric for Android components (Lifecycle, Network)
- Test class naming: `<ClassUnderTest>Test.kt` (e.g., `StreamRetryProcessorImplTest.kt`)
- Always test both success and failure paths
- Use `runTest` from `kotlinx-coroutines-test` for coroutine testing

### Testing Android Components
Android Lifecycle/Network components require Robolectric:
```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class LifecycleTest {
    @Test
    fun testLifecycleMonitor() {
        // Test with real Android components
    }
}
```

### Mocking Strategy
Use factory functions with test implementations:
```kotlin
class TestSerialQueue : StreamSerialProcessingQueue {
    val submittedWork = mutableListOf<suspend () -> Any>()

    override suspend fun <T : Any> submit(job: suspend () -> T): Result<T> {
        submittedWork.add(job)
        return Result.success(Unit as T)
    }

    override suspend fun start(): Result<Unit> = Result.success(Unit)
    override suspend fun stop(timeout: Long?): Result<Unit> = Result.success(Unit)
}

@Test
fun testComponent() {
    val testQueue = TestSerialQueue()
    val component = MyComponent(testQueue)

    component.doSomething()

    assertEquals(1, testQueue.submittedWork.size)
}
```

### Testing Coroutines
```kotlin
@Test
fun testSuspendFunction() = runTest {
    val result = suspendingOperation()
    assertTrue(result.isSuccess)
}
```

## Common Pitfalls

### 1. Forgetting Component Start/Stop
Components don't work until started. Always follow: create → start() → use → stop()

### 2. Sharing Subscription Managers
Never share the same `StreamSubscriptionManager` instance across different components - creates event loops. Create separate instances per component.

### 3. Memory Leaks with Strong References
Using `KEEP_UNTIL_CANCELLED` without cancelling causes leaks. Either use `AUTO_REMOVE` for UI or explicitly cancel.

### 4. Token Refresh Storms
Share a single `StreamTokenManager` instance across components. Multiple instances = multiple concurrent refreshes.

### 5. Token Error Code Confusion
Token errors use **codes 40, 41, 42** (from `StreamEndpointErrorData.code`), **NOT HTTP status codes 401/403**. Check for these specific codes when detecting token errors.

### 6. Blocking OkHttp Thread Pool
`StreamAuthInterceptor` uses `runBlocking` for token operations. Long token fetches block HTTP thread. Keep `TokenProvider.getToken()` fast.

### 7. Excessive Retry Attempts
Always use `giveUpFunction` in `StreamRetryPolicy` to stop on non-retryable errors:
```kotlin
val policy = StreamRetryPolicy.Exponential(
    maxRetries = 10,
    giveUpFunction = { attempt, error ->
        error is UnauthorizedException ||
        error is ForbiddenException ||
        error is NotFoundException
    }
)
```

### 8. Scope Lifecycle Mismatches
Match component lifetime to coroutine scope lifetime. Never use `GlobalScope`. Use `viewModelScope`, lifecycle scopes, or custom managed scopes.

### 9. Ignoring Result Failures
Always handle `Result` - silent failures lead to hard-to-debug issues:
```kotlin
component.start()
    .onSuccess { logger.i { "Started" } }
    .onFailure { error ->
        logger.e(error) { "Failed to start" }
        // Handle error appropriately
    }
```

### 10. Race Conditions in Socket Close
Socket close callbacks may fire after explicit disconnect. Use atomic flags to detect "closed by us" vs "closed by server":
```kotlin
// StreamSocketSession.kt:67-71
private val closingByUs = AtomicBoolean(false)

override fun onClosed(code: Int, reason: String) {
    if (!closingByUs.get()) {
        // Server closed socket, not us
        notifyState(Disconnected(cause))
    }
}
```

## Key File Locations

### Core Components
- **Client interface**: `stream-android-core/src/main/java/io/getstream/android/core/api/StreamClient.kt`
- **Client implementation**: `stream-android-core/src/main/java/io/getstream/android/core/internal/StreamClientImpl.kt`
- **Socket session**: `stream-android-core/src/main/java/io/getstream/android/core/internal/socket/StreamSocketSession.kt`
- **WebSocket wrapper**: `stream-android-core/src/main/java/io/getstream/android/core/internal/socket/StreamWebSocketImpl.kt`

### State & Models
- **Connection state**: `stream-android-core/src/main/java/io/getstream/android/core/api/model/connection/StreamConnectionState.kt`
- **Connected user**: `stream-android-core/src/main/java/io/getstream/android/core/api/model/connection/StreamConnectedUser.kt`
- **Retry policy**: `stream-android-core/src/main/java/io/getstream/android/core/api/model/retry/StreamRetryPolicy.kt`
- **Error data**: `stream-android-core/src/main/java/io/getstream/android/core/api/model/exceptions/StreamEndpointErrorData.kt`

### Processing Patterns
- **Single-flight**: `stream-android-core/src/main/java/io/getstream/android/core/internal/processing/StreamSingleFlightProcessorImpl.kt`
- **Serial queue**: `stream-android-core/src/main/java/io/getstream/android/core/internal/processing/StreamSerialProcessingQueueImpl.kt`
- **Batcher**: `stream-android-core/src/main/java/io/getstream/android/core/internal/processing/StreamBatcherImpl.kt`
- **Retry**: `stream-android-core/src/main/java/io/getstream/android/core/internal/processing/StreamRetryProcessorImpl.kt`

### Authentication
- **Token manager**: `stream-android-core/src/main/java/io/getstream/android/core/internal/authentication/StreamTokenManagerImpl.kt`
- **Auth interceptor**: `stream-android-core/src/main/java/io/getstream/android/core/internal/http/interceptor/StreamAuthInterceptor.kt`

### Monitoring
- **Health monitor**: `stream-android-core/src/main/java/io/getstream/android/core/internal/socket/monitor/StreamHealthMonitorImpl.kt`
- **Network monitor**: `stream-android-core/src/main/java/io/getstream/android/core/internal/observers/network/StreamNetworkMonitorImpl.kt`
- **Lifecycle monitor**: `stream-android-core/src/main/java/io/getstream/android/core/internal/observers/lifecycle/StreamLifecycleMonitorImpl.kt`
- **Recovery evaluator**: `stream-android-core/src/main/java/io/getstream/android/core/internal/recovery/StreamConnectionRecoveryEvaluatorImpl.kt`

### Serialization
- **Composite serialization**: `stream-android-core/src/main/java/io/getstream/android/core/internal/serialization/StreamCompositeEventSerializationImpl.kt`
- **Moshi implementation**: `stream-android-core/src/main/java/io/getstream/android/core/internal/serialization/StreamMoshiJsonSerializationImpl.kt`

### Tests
- **Test directory**: `stream-android-core/src/test/java/io/getstream/android/core/`
- **Example test**: `stream-android-core/src/test/java/io/getstream/android/core/internal/processing/StreamRetryProcessorImplTest.kt`

## Configuration Files

- **build.gradle.kts**: Uses explicit API mode, enables coroutines, KSP for Moshi codegen, JVM target 11
- **detekt.yml**: `config/detekt/detekt.yml` - static analysis rules with auto-correct enabled
- **lint.xml**: Root `lint.xml` - Android lint configuration (abort on error, warnings as errors)
- **consumer-rules.pro**: ProGuard rules for library consumers
- **gradle.properties**: JVM args, AndroidX, Kotlin code style

## Branch & PR Strategy

- **Main branch**: `develop` (not `main` or `master`)
- **Current branch**: Development happens on feature branches (e.g., `stream-watcher`)
- **PR target**: Always target `develop` for pull requests
- **Commit messages**: Follow existing style, use imperative mood ("Add feature" not "Added feature")
- **No force push**: To `develop` or `main` branches

## Special Considerations

### Visibility Annotations Usage
When adding new APIs, choose the appropriate annotation:
- `@StreamPublishedApi` - Stable APIs that product SDKs expose to integrators (breaking changes require major version bump)
- `@StreamInternalApi` - Internal infrastructure, may change without notice (most common for new features)
- `@StreamDelicateApi` - Advanced APIs requiring careful use (e.g., direct socket access)

### Moshi Code Generation
Use `@JsonClass(generateAdapter = true)` for data classes:
```kotlin
@JsonClass(generateAdapter = true)
data class MyEvent(
    val type: String,
    val data: String
)
```

KSP generates adapters at compile time. Never write adapters manually unless dealing with polymorphism or custom serialization logic.

### Structured Logging
Use lambda-based logging for performance:
```kotlin
logger.d { "Expensive: ${computeExpensiveString()}" }  // ✅ Only called if DEBUG enabled
logger.d("Expensive: ${computeExpensiveString()}")     // ❌ Always computed
```

### SupervisorJob Pattern
Always use `SupervisorJob` for scopes powering multiple components:
```kotlin
val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
```

This prevents one component's failure from cancelling siblings. Without `SupervisorJob`, a failure in the health monitor would cancel the batcher, socket session, etc.
