# Stream Android Core

> **Internal Stream SDK**
> This repository is **for Stream products only**. It is not intended for public consumption or direct integration by third-party apps.

## What is Stream?

[Stream](https://getstream.io) provides APIs and SDKs for building real-time communication features at scale:

- **[Stream Chat](https://getstream.io/chat/)** - Production-ready messaging and chat with channels, threads, reactions, moderation, and more
- **[Stream Video](https://getstream.io/video/)** - Video calling, audio rooms, and livestreaming with ultra-low latency
- **[Stream Feeds](https://getstream.io/activity-feeds/)** - Scalable activity feeds and social features for apps

Each of these products offers platform-specific SDKs (Android, iOS, React, Flutter, etc.) that developers integrate into their applications.

## What is Stream Android Core?

**Stream Android Core** is the **internal foundational library** that powers all of Stream's Android SDKs. It's the shared infrastructure layer that provides common functionality needed across Chat, Video, and Feeds:

### Purpose

Rather than duplicating infrastructure code across multiple SDKs, Stream Android Core centralizes:

- **Real-time connectivity**: WebSocket management with automatic reconnection and health monitoring
- **Authentication**: Token lifecycle management with automatic refresh
- **State management**: Connection state, network availability, app lifecycle tracking
- **Reliability**: Retry policies, exponential backoff, and connection recovery
- **Performance**: Request deduplication, batching, serial processing queues
- **Thread safety**: Cross-thread execution utilities and subscription management
- **Observability**: Structured logging and event propagation

### Architecture Position

```
┌─────────────────────────────────────────────────────────┐
│   Application Code (Chat UI, Video Calls, Feeds UI)    │
└────────────────────────┬────────────────────────────────┘
                         │
┌────────────────────────┴────────────────────────────────┐
│  Stream Product SDKs (Chat SDK, Video SDK, Feeds SDK)  │ ← Public APIs
└────────────────────────┬────────────────────────────────┘
                         │
┌────────────────────────┴────────────────────────────────┐
│         Stream Android Core (This Repository)           │ ← Internal Infrastructure
│  • WebSocket connections    • Serial processing         │
│  • Token management          • Retry logic              │
│  • Lifecycle monitoring      • Event batching           │
│  • Network detection         • Thread utilities         │
└────────────────────────┬────────────────────────────────┘
                         │
┌────────────────────────┴────────────────────────────────┐
│         Stream Backend APIs (Chat, Video, Feeds)        │
└─────────────────────────────────────────────────────────┘
```

## Overview

**Stream Android Core** provides shared primitives and infrastructure for Stream's Android SDKs:

- Authentication & token management
- Connection lifecycle & event handling
- Network & app lifecycle monitoring
- Retry policies with backoff strategies
- Serial processing queues
- Single-flight execution (request deduplication)
- Batching & debouncing
- Thread-safe subscription management
- WebSocket connections with health monitoring
- Structured logging

### API Visibility: Internal vs Published

Stream Android Core uses annotations to distinguish between stable public APIs and internal implementation details.

**`@StreamPublishedApi`** - Stable APIs that product SDKs can expose to integrators. These have stability guarantees and follow semantic versioning. Examples: `StreamConnectionState`, `StreamConnectedUser`.

**`@StreamInternalApi`** - Internal infrastructure not meant for integrators. May change without notice and require `@OptIn(StreamInternalApi::class)`. Examples: `StreamRetryProcessor`, `StreamSerialProcessingQueue`, threading utilities.

**`@StreamDelicateApi`** - Advanced low-level APIs requiring careful use. Require `@OptIn(StreamDelicateApi::class)` and generate compiler warnings.

### Who Should Use This?

**Integrator/app developers**: Use [Stream Chat SDK](https://github.com/GetStream/stream-chat-android), [Stream Video SDK](https://github.com/GetStream/stream-video-android), or [Stream Feeds SDK](https://github.com/GetStream/stream-android). Access published Core types through your product SDK's public API.

**Stream SDK maintainers**: Use Core as your infrastructure foundation. Expose `@StreamPublishedApi` types in your SDK's public API, keep `@StreamInternalApi` types private.

## Table of Contents

- [Quick Start](#quick-start)
- [Requirements](#requirements)
- [Core Concepts](#core-concepts)
- [Feature Guides](#feature-guides)
  - [Lifecycle & Network Monitoring](#lifecycle--network-monitoring)
  - [Subscription Management](#subscription-management)
  - [Serial Processing Queue](#serial-processing-queue)
  - [Single-Flight Processor](#single-flight-processor)
  - [Retry Processor](#retry-processor)
  - [Batcher](#batcher)
  - [Threading Utilities](#threading-utilities)
  - [Token Management](#token-management)
  - [WebSocket Connections](#websocket-connections)
  - [Logging](#logging)
- [Common Pitfalls & Best Practices](#common-pitfalls--best-practices)
- [Advanced Topics](#advanced-topics)
- [Testing](#testing)
- [Project Structure](#project-structure)
- [License](#license)

---

## Quick Start

### Minimal Setup

Here's a minimal example to get started with Stream Android Core:

```kotlin
import io.getstream.android.core.api.log.StreamLoggerProvider
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

// 1. Set up logging
val logProvider = StreamLoggerProvider.defaultAndroidLogger(
    minLevel = StreamLogger.LogLevel.Verbose
)

// 2. Create a coroutine scope
val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

// 3. Create a subscription manager
val subscriptionManager = StreamSubscriptionManager<MyListener>(
    logger = logProvider.taggedLogger("MySubscriptions")
)
```

### Basic Client Instantiation

```kotlin
val singleFlight = StreamSingleFlightProcessor(scope)
val tokenManager = StreamTokenManager(userId, tokenProvider, singleFlight)

val serialQueue = StreamSerialProcessingQueue(
    logger = logProvider.taggedLogger("SerialQueue"),
    scope = scope
)

val client = StreamClient(
    scope = scope,
    apiKey = apiKey,
    userId = userId,
    tokenProvider = tokenProvider,
    logProvider = logProvider,
    tokenManager = tokenManager,
    singleFlight = singleFlight,
    serialQueue = serialQueue,
    // ... other dependencies
)
```

---

## Requirements

- **minSdk**: 21+ (Android 5.0 Lollipop)
- **compileSdk/targetSdk**: 36
- **Kotlin**: 2.2.0+
- **Coroutines**: 1.10+
- **AGP**: 8.11+
- **Lifecycle**: 2.9+

---

## Core Concepts

### Component Lifecycle

Most components in Stream Android Core implement `StreamStartableComponent`:

```kotlin
interface StreamStartableComponent {
    fun start(): Result<Unit>
    fun stop(): Result<Unit>
}
```

**Pattern**: Always call `start()` before using a component and `stop()` when done:

```kotlin
val component = StreamSerialProcessingQueue(logger, scope)

component.start()
    .onSuccess { /* component is ready */ }
    .onFailure { error -> logger.e(error) { "Failed to start" } }

// Use the component...

component.stop()
    .onSuccess { /* cleanup complete */ }
    .onFailure { error -> logger.w(error) { "Failed to stop gracefully" } }
```

### Result-Based Error Handling

The library uses Kotlin's `Result<T>` type consistently:

```kotlin
// Success case
val result: Result<String> = runCatching { "success" }
result.getOrNull() // "success"

// Failure case
val result: Result<String> = runCatching { throw Exception("error") }
result.exceptionOrNull() // Exception("error")

// Handling results
result
    .onSuccess { value -> println(value) }
    .onFailure { error -> logger.e(error) { "Operation failed" } }
```

### Thread Safety

- **All public APIs are thread-safe** unless explicitly documented otherwise
- Components use appropriate synchronization internally
- **Threading utilities** ensure operations run on the correct thread (e.g., main thread for lifecycle operations)

---

## Feature Guides

### Lifecycle & Network Monitoring

Monitor app lifecycle (foreground/background) and network connectivity changes.

#### Lifecycle Monitoring

```kotlin
import io.getstream.android.core.api.observers.lifecycle.StreamLifecycleMonitor
import io.getstream.android.core.api.observers.lifecycle.StreamLifecycleListener

// Create the monitor
val lifecycleMonitor = StreamLifecycleMonitor(
    logger = logger,
    lifecycle = processLifecycleOwner.lifecycle,
    subscriptionManager = StreamSubscriptionManager(logger)
)

// Subscribe to lifecycle events
val listener = object : StreamLifecycleListener {
    override fun onForeground() {
        logger.i { "App moved to foreground" }
        // Reconnect sockets, resume sync, etc.
    }

    override fun onBackground() {
        logger.i { "App moved to background" }
        // Pause non-critical operations
    }
}

lifecycleMonitor.subscribe(listener).getOrThrow()
lifecycleMonitor.start().getOrThrow()

// Get current state
val state = lifecycleMonitor.getCurrentState()
when (state) {
    is StreamLifecycleState.Foreground -> { /* app is active */ }
    is StreamLifecycleState.Background -> { /* app is backgrounded */ }
    is StreamLifecycleState.Unknown -> { /* not yet determined */ }
}
```

#### Network Monitoring

```kotlin
import io.getstream.android.core.api.observers.network.StreamNetworkMonitor
import io.getstream.android.core.api.observers.network.StreamNetworkMonitorListener

val networkMonitor = StreamNetworkMonitor(
    context = context,
    logger = logger,
    subscriptionManager = StreamSubscriptionManager(logger)
)

val listener = object : StreamNetworkMonitorListener {
    override suspend fun onNetworkConnected(snapshot: StreamNetworkInfo.Snapshot?) {
        logger.i { "Network connected: $snapshot" }
        // Retry failed requests, reconnect, etc.
    }

    override suspend fun onNetworkLost(permanent: Boolean) {
        logger.w { "Network lost (permanent: $permanent)" }
        // Queue operations for later, show offline UI
    }
}

networkMonitor.subscribe(listener).getOrThrow()
networkMonitor.start().getOrThrow()
```

**Key Points**:
- Lifecycle operations (add/remove observers) **must** run on the main thread
- The library handles thread dispatching automatically using `runOnMainLooper`
- Network listener methods are `suspend` functions and run on background threads

---

### Subscription Management

Thread-safe listener registration with automatic cleanup.

#### Basic Usage

```kotlin
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager.Options
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager.Options.Retention

// Create manager
val subscriptionManager = StreamSubscriptionManager<MyListener>(
    logger = logger,
    maxStrongSubscriptions = 250, // Strong references
    maxWeakSubscriptions = 250    // Weak references (auto-cleanup)
)

// Subscribe with options
val subscription = subscriptionManager.subscribe(
    listener = myListener,
    options = Options(retention = Retention.KEEP_UNTIL_CANCELLED)
).getOrThrow()

// Notify all listeners
subscriptionManager.forEach { listener ->
    listener.onEvent(event)
}

// Unsubscribe
subscription.cancel()

// Clear all
subscriptionManager.clear()
```

#### Retention Policies

```kotlin
enum class Retention {
    // Weak reference - auto-removed when GC'd
    AUTO_REMOVE,

    // Strong reference - kept until explicitly cancelled
    KEEP_UNTIL_CANCELLED
}
```

**When to use each**:
- `AUTO_REMOVE`: UI components, fragments, activities (automatic cleanup)
- `KEEP_UNTIL_CANCELLED`: Long-lived services, singletons (explicit lifecycle)

---

### Serial Processing Queue

Ordered, single-threaded coroutine pipeline for sequential work.

#### Basic Usage

```kotlin
import io.getstream.android.core.api.processing.StreamSerialProcessingQueue

val queue = StreamSerialProcessingQueue(
    logger = logger,
    scope = scope
)

queue.start().getOrThrow()

// Submit work - guaranteed to run in order
queue.submit {
    // Work 1 - runs first
    processItem1()
}

queue.submit {
    // Work 2 - runs after work 1 completes
    processItem2()
}

// Stop with timeout
queue.stop(timeout = 5000).getOrThrow()
```

#### Use Cases

- Database operations that must be sequential
- State mutations that can't be concurrent
- Processing events in order
- Cache updates

**Pattern**: Natural backpressure (FIFO queue)

---

### Single-Flight Processor

Deduplicates concurrent identical requests - only one in-flight operation per key.

#### Basic Usage

```kotlin
import io.getstream.android.core.api.processing.StreamSingleFlightProcessor

val singleFlight = StreamSingleFlightProcessor(scope)

// Multiple concurrent calls with same key share the same result
launch {
    val result = singleFlight.run("user-123") {
        // Expensive operation runs only once
        fetchUserFromNetwork("user-123")
    }
}

launch {
    val result = singleFlight.run("user-123") {
        // This waits for the first call, doesn't execute
        fetchUserFromNetwork("user-123")
    }
}
```

#### Use Cases

- Token refresh (multiple requests trigger one refresh)
- User data fetching
- Configuration loading
- Any expensive operation called concurrently

**Pattern**: First caller executes, subsequent callers await the same result

---

### Retry Processor

Automatic retry with linear or exponential backoff.

#### Linear Backoff

```kotlin
import io.getstream.android.core.api.processing.StreamRetryProcessor
import io.getstream.android.core.api.model.StreamRetryPolicy

val retryProcessor = StreamRetryProcessor(logger)

val policy = StreamRetryPolicy.Linear(
    minRetries = 3,
    maxRetries = 10,
    initialDelayMillis = 1000, // 1s, 2s, 3s, ...
    maxDelayMillis = 30000      // Cap at 30s
)

val result = retryProcessor.retry(policy) {
    // Operation that may fail
    sendRequest()
}
```

#### Exponential Backoff

```kotlin
val policy = StreamRetryPolicy.Exponential(
    minRetries = 3,
    maxRetries = 10,
    initialDelayMillis = 1000, // 1s, 2s, 4s, 8s, 16s, ...
    maxDelayMillis = 60000,     // Cap at 60s
    giveUpFunction = { attempt, error ->
        // Stop retrying on specific errors
        error is UnauthorizedException
    }
)
```

#### Use Cases

- Network requests with transient failures
- WebSocket reconnection
- Resource acquisition
- Third-party API calls

---

### Batcher

Collects items into batches based on size and/or time windows.

#### Basic Usage

```kotlin
import io.getstream.android.core.api.processing.StreamBatcher

val batcher = StreamBatcher<MyEvent>(
    scope = scope,
    batchSize = 10,           // Flush after 10 items
    initialDelayMs = 100,     // Initial wait before first flush
    maxDelayMs = 1000,        // Maximum time before forcing flush
    autoStart = true          // Start automatically on first enqueue
)

// Register batch handler
batcher.onBatch { batch, delayMs, count ->
    // Process batch
    sendBatchToServer(batch)
}

// Enqueue items
batcher.enqueue(event1)
batcher.enqueue(event2)
// Batched and sent together

batcher.stop().getOrThrow()
```

#### Use Cases

- Analytics event batching
- Log aggregation
- Bulk API requests
- Reducing network calls

**Pattern**: Debouncing + size-based flushing

---

### Threading Utilities

Safe cross-thread execution with timeout protection.

#### Running on Main Thread

```kotlin
import io.getstream.android.core.api.utils.runOnMainLooper

// From any thread, execute on main thread
val result = runOnMainLooper {
    // Runs on main thread
    lifecycle.addObserver(observer)
}

result
    .onSuccess { /* observer added */ }
    .onFailure { error -> logger.e(error) { "Failed to add observer" } }
```

#### Running on Custom Looper

```kotlin
import io.getstream.android.core.api.utils.runOn
import android.os.HandlerThread

val workerThread = HandlerThread("worker").apply { start() }
val workerLooper = workerThread.looper

val result = runOn(workerLooper) {
    // Runs on worker thread
    performBackgroundWork()
}
```

#### Key Features

- **Automatic thread detection**: If already on target thread, executes immediately
- **Blocking with timeout**: Caller blocks until completion (5 second timeout)
- **Exception propagation**: Exceptions captured in `Result`
- **Cancellation-safe**: `CancellationException` is rethrown

**Pitfall**: This is a **blocking** operation. For non-blocking alternatives, use coroutines with appropriate dispatchers:

```kotlin
// Non-blocking alternative
withContext(Dispatchers.Main) {
    lifecycle.addObserver(observer)
}
```

---

### Token Management

Handles authentication token lifecycle with automatic refresh.

#### Basic Setup

```kotlin
import io.getstream.android.core.api.authentication.StreamTokenManager
import io.getstream.android.core.api.authentication.StreamTokenProvider

val tokenProvider = object : StreamTokenProvider {
    override suspend fun getToken(): String {
        // Fetch token from backend
        return api.getAuthToken(userId)
    }
}

val tokenManager = StreamTokenManager(
    userId = userId,
    tokenProvider = tokenProvider,
    singleFlight = singleFlight // Prevents multiple concurrent refreshes
)

// Get current token (triggers refresh if needed)
val token = tokenManager.getToken()
```

#### Token Refresh Flow

```kotlin
// Set initial token
tokenManager.setToken("initial-token")

// Token becomes invalid
tokenManager.invalidateToken()

// Next call triggers refresh via tokenProvider
val newToken = tokenManager.getToken() // Calls tokenProvider.getToken()
```

**Key Points**:
- Token refresh is deduplicated via single-flight processor
- Multiple concurrent requests wait for single refresh
- Thread-safe token updates

---

### WebSocket Connections

Reliable WebSocket connections with health monitoring.

#### Creating a WebSocket

```kotlin
import io.getstream.android.core.api.socket.StreamWebSocketFactory
import io.getstream.android.core.api.socket.listeners.StreamWebSocketListener

val socketFactory = StreamWebSocketFactory(logger)

val listener = object : StreamWebSocketListener {
    override fun onConnected(webSocket: StreamWebSocket) {
        logger.i { "WebSocket connected" }
    }

    override fun onMessage(webSocket: StreamWebSocket, text: String) {
        logger.d { "Received: $text" }
    }

    override fun onClosing(webSocket: StreamWebSocket, code: Int, reason: String) {
        logger.w { "Closing: $code - $reason" }
    }

    override fun onFailure(webSocket: StreamWebSocket, t: Throwable) {
        logger.e(t) { "WebSocket error" }
    }
}

val webSocket = socketFactory.create(
    url = "wss://example.com/socket",
    listener = listener
)

webSocket.connect()
```

#### Health Monitoring

```kotlin
import io.getstream.android.core.api.socket.monitor.StreamHealthMonitor

val healthMonitor = StreamHealthMonitor(
    logger = logger,
    scope = scope,
    pingInterval = 30_000, // Ping every 30s
    pongTimeout = 10_000   // Expect pong within 10s
)

healthMonitor.start().getOrThrow()

// Monitor will detect unhealthy connections
```

---

### Logging

Structured, configurable logging throughout the library.

#### Setup

```kotlin
import io.getstream.android.core.api.log.StreamLoggerProvider
import io.getstream.android.core.api.log.StreamLogger

// Default Android logger
val logProvider = StreamLoggerProvider.defaultAndroidLogger(
    minLevel = StreamLogger.LogLevel.Debug,
    honorAndroidIsLoggable = true // Respects Log.isLoggable()
)

// Tagged logger for specific component
val logger = logProvider.taggedLogger("MyComponent")
```

#### Usage

```kotlin
logger.v { "Verbose message" }        // Verbose
logger.d { "Debug message" }          // Debug
logger.i { "Info message" }           // Info
logger.w { "Warning message" }        // Warn
logger.e(exception) { "Error" }       // Error with exception

// Lazy evaluation - only called if log level is enabled
logger.d {
    "Expensive: ${computeExpensiveString()}"
}
```

#### Log Levels

```kotlin
enum class LogLevel {
    VERBOSE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    NONE
}
```

---

## Common Pitfalls & Best Practices

### 1. Lifecycle Threading Violations

**Problem**: Android Lifecycle components require operations on the main thread.

```kotlin
// ❌ WRONG - May crash with "Can only call from main thread"
thread {
    lifecycle.addObserver(observer)
}
```

**Solution**: Use `runOnMainLooper`:

```kotlin
// ✅ CORRECT
thread {
    runOnMainLooper {
        lifecycle.addObserver(observer)
    }.getOrThrow()
}
```

**Why**: The library uses `runOnMainLooper` internally in `StreamLifecycleMonitorImpl` to ensure thread safety.

---

### 2. Shared Subscription Managers

**Problem**: Reusing the same subscription manager instance can cause event loops.

```kotlin
// ❌ WRONG
val sharedManager = StreamSubscriptionManager<MyListener>(logger)

val client = StreamClient(
    subscriptionManager = sharedManager,
    // ...
)

val session = StreamSession(
    subscriptionManager = sharedManager, // Same instance!
    // ...
)
```

**Solution**: Create separate instances for different components:

```kotlin
// ✅ CORRECT
val clientManager = StreamSubscriptionManager<ClientListener>(logger)
val sessionManager = StreamSubscriptionManager<SessionListener>(logger)
```

**Why**: Shared managers can cause nested event notifications leading to stack overflows or infinite loops.

---

### 3. Forgetting to Start/Stop Components

**Problem**: Components don't work until started, resources leak if not stopped.

```kotlin
// ❌ WRONG
val queue = StreamSerialProcessingQueue(logger, scope)
queue.submit { /* Never runs! */ }
```

**Solution**: Always follow the start/use/stop pattern:

```kotlin
// ✅ CORRECT
val queue = StreamSerialProcessingQueue(logger, scope)
queue.start().getOrThrow()

queue.submit { /* Runs correctly */ }

queue.stop().getOrThrow()
```

---

### 4. Blocking Main Thread with `runOn`

**Problem**: `runOn` and `runOnMainLooper` **block** the calling thread.

```kotlin
// ❌ WRONG - Blocks main thread
fun onButtonClick() {
    runOn(backgroundLooper) {
        Thread.sleep(10_000) // Main thread blocked for 10s!
    }
}
```

**Solution**: Use coroutines for non-blocking cross-thread operations:

```kotlin
// ✅ CORRECT
fun onButtonClick() {
    lifecycleScope.launch {
        withContext(Dispatchers.IO) {
            performWork()
        }
    }
}
```

**When to use `runOn`**: Only when you need synchronous, guaranteed-ordered execution (e.g., lifecycle operations).

---

### 5. Ignoring Result Failures

**Problem**: Silent failures lead to hard-to-debug issues.

```kotlin
// ❌ WRONG
component.start() // Ignores potential failure
```

**Solution**: Always handle `Result`:

```kotlin
// ✅ CORRECT
component.start()
    .onSuccess { logger.i { "Started successfully" } }
    .onFailure { error ->
        logger.e(error) { "Failed to start component" }
        // Handle error appropriately
    }
```

---

### 6. Coroutine Scope Lifecycle Mismatches

**Problem**: Using wrong scope leads to leaks or premature cancellation.

```kotlin
// ❌ WRONG - GlobalScope never cancels
val queue = StreamSerialProcessingQueue(
    logger = logger,
    scope = GlobalScope
)
```

**Solution**: Use appropriately scoped coroutines:

```kotlin
// ✅ CORRECT
class MyComponent : ViewModel() {
    private val queue = StreamSerialProcessingQueue(
        logger = logger,
        scope = viewModelScope // Cancelled when ViewModel cleared
    )
}
```

**Pattern**: Match component lifetime to scope lifetime.

---

### 7. Race Conditions in Serial Queue

**Problem**: Submitting from multiple threads without synchronization.

```kotlin
// ❌ POTENTIALLY WRONG
thread { queue.submit { work1() } }
thread { queue.submit { work2() } }
// Order not guaranteed!
```

**Solution**: Queue operations are thread-safe, but if order matters across threads, use explicit synchronization:

```kotlin
// ✅ CORRECT - If order must be guaranteed
synchronized(lock) {
    queue.submit { work1() }
    queue.submit { work2() }
}
```

---

### 8. Token Refresh Storms

**Problem**: Multiple components triggering token refresh simultaneously.

```kotlin
// ❌ WRONG - Each component has its own token manager
val tokenManager1 = StreamTokenManager(userId, tokenProvider, singleFlight1)
val tokenManager2 = StreamTokenManager(userId, tokenProvider, singleFlight2)
// Both refresh tokens independently!
```

**Solution**: Share a single token manager instance:

```kotlin
// ✅ CORRECT
val tokenManager = StreamTokenManager(userId, tokenProvider, singleFlight)

val component1 = Component1(tokenManager)
val component2 = Component2(tokenManager)
```

**Why**: Single-flight processor in token manager ensures one refresh at a time.

---

### 9. Memory Leaks with Strong References

**Problem**: Listeners held with `KEEP_UNTIL_CANCELLED` but never cancelled.

```kotlin
// ❌ WRONG
class MyActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        subscriptionManager.subscribe(
            listener = this,
            options = Options(retention = KEEP_UNTIL_CANCELLED)
        )
        // Never cancelled - activity leaks!
    }
}
```

**Solution**: Either use `AUTO_REMOVE` or cancel explicitly:

```kotlin
// ✅ CORRECT - Option 1: Auto cleanup
subscriptionManager.subscribe(
    listener = this,
    options = Options(retention = AUTO_REMOVE)
)

// ✅ CORRECT - Option 2: Manual cleanup
class MyActivity : AppCompatActivity() {
    private var subscription: StreamSubscription? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        subscription = subscriptionManager.subscribe(
            listener = this,
            options = Options(retention = KEEP_UNTIL_CANCELLED)
        ).getOrThrow()
    }

    override fun onDestroy() {
        super.onDestroy()
        subscription?.cancel()
    }
}
```

---

### 10. Excessive Retry Attempts

**Problem**: Retrying operations that will never succeed.

```kotlin
// ❌ WRONG - Retries 401 Unauthorized forever
val policy = StreamRetryPolicy.Exponential(
    maxRetries = Int.MAX_VALUE,
    initialDelayMillis = 1000
)

retryProcessor.retry(policy) {
    authenticatedRequest() // Always returns 401
}
```

**Solution**: Use `giveUpFunction` to stop on non-retryable errors:

```kotlin
// ✅ CORRECT
val policy = StreamRetryPolicy.Exponential(
    maxRetries = 10,
    initialDelayMillis = 1000,
    giveUpFunction = { attempt, error ->
        when (error) {
            is UnauthorizedException,
            is ForbiddenException,
            is NotFoundException -> true // Don't retry
            else -> false // Continue retrying
        }
    }
)
```

---

## Advanced Topics

### Custom Implementations

All public interfaces can be replaced with custom implementations:

```kotlin
// Custom subscription manager
class MySubscriptionManager<T> : StreamSubscriptionManager<T> {
    override fun subscribe(listener: T, options: Options): Result<StreamSubscription> {
        // Custom logic
    }

    override fun clear() {
        // Custom cleanup
    }

    override fun forEach(action: (T) -> Unit): Result<Unit> {
        // Custom iteration
    }
}
```

### Factory Functions

Prefer factory functions over direct instantiation:

```kotlin
// ✅ CORRECT - Factory function
val queue = StreamSerialProcessingQueue(logger, scope)

// ❌ AVOID - Direct instantiation
val queue = StreamSerialProcessingQueueImpl(logger, scope)
```

**Why**: Factory functions provide stability across implementation changes.

---

## Testing

### Mocking Components

Use factory functions with test implementations:

```kotlin
class TestSerialQueue : StreamSerialProcessingQueue {
    val submittedWork = mutableListOf<suspend () -> Any>()

    override suspend fun <T : Any> submit(job: suspend () -> T): Result<T> {
        submittedWork.add(job)
        @Suppress("UNCHECKED_CAST")
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

### Robolectric for Lifecycle Tests

Android lifecycle components require Robolectric:

```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class LifecycleTest {
    @Test
    fun testLifecycleMonitor() {
        val monitor = StreamLifecycleMonitor(...)
        // Test with real Android components
    }
}
```

---

## Project Structure

```
stream-android-core/
├── app/                               # Demo app for manual testing
├── stream-android-core/               # Core library
│   └── src/main/java/io/getstream/android/core/
│       ├── api/                       # Public API
│       │   ├── authentication/        # Token management
│       │   ├── observers/             # Lifecycle & network monitoring
│       │   │   ├── lifecycle/
│       │   │   └── network/
│       │   ├── processing/            # Queue, retry, single-flight, batcher
│       │   ├── subscribe/             # Subscription management
│       │   ├── socket/                # WebSocket connections
│       │   ├── log/                   # Logging infrastructure
│       │   ├── utils/                 # Threading, Result utilities
│       │   └── model/                 # Data models
│       └── internal/                  # Internal implementations
├── stream-android-core-annotations/   # Annotations & processors
├── stream-android-core-lint/          # Custom lint rules
├── config/                            # Static analysis configs
└── gradle/                            # Gradle configuration
```

---

## License

Copyright (c) 2014-2025 Stream.io Inc. All rights reserved.

Licensed under the Stream License;
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

https://github.com/GetStream/stream-core-android/blob/main/LICENSE

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
