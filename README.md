# Stream Android Core

> **Internal Stream SDK**  
> This repository is **for Stream products only**. It is not intended for public consumption or direct integration by third-party apps.

## Overview
**Stream Android Core** is the foundational library powering Stream’s Android SDKs (Chat, Video, Feeds, etc.). It provides shared primitives and infrastructure: authentication/token handling, connection & event lifecycle, retries/backoff, single-flight execution, serial processing queues, batching, and logging.

## Project structure
- **app/** – Demo app used for local development and manual testing.
- **stream-android-core/** – Core library: models, processors, lifecycle, queues, batching.
- **stream-android-core-annotations/** – Internal annotations / processors supporting the core.
- **stream-android-core-lint/** – Custom lint rules tailored for Stream codebases.
- **config/** – Static analysis and style configs (ktlint, detekt, license headers).
- **gradle/** – Gradle wrapper and version catalogs.

## Requirements
- **minSdk**: 21+
- **Kotlin**: 1.9+
- **Coroutines**: 1.8+
- **AGP**: 8.x+


## What it offers
- Shared models and value types
- Token management and client/session lifecycle hooks
- **Serial processing queue** for ordered, single-threaded coroutine work
- **Single-flight processor** to dedupe concurrent identical tasks
- **Retry processor** with linear/exponential backoff and give-up predicates
- **Batcher** for efficient event aggregation
- Internal annotations and custom lint rules
- Demo app to validate changes end-to-end

## Instantiating a client

```kotlin
val logProvider = StreamLoggerProvider.defaultAndroidLogger(
    minLevel = StreamLogger.LogLevel.Verbose,
    honorAndroidIsLoggable = true,
)

val clientSubscriptionManager = StreamSubscriptionManager<StreamClientListener>(
    logger = logProvider.taggedLogger("SCClientSubscriptions"),
    maxStrongSubscriptions = 250,
    maxWeakSubscriptions = 250,
)

val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

val singleFlight   = StreamSingleFlightProcessor(scope)
val tokenManager   = StreamTokenManager(userId, tokenProvider, singleFlight)

val serialQueue    = StreamSerialProcessingQueue(
    logger = logProvider.taggedLogger("SCSerialProcessing"),
    scope = scope,
)

val retryProcessor = StreamRetryProcessor(
    logger = logProvider.taggedLogger("SCRetryProcessor")
)

val connectionIdHolder = StreamConnectionIdHolder()
val socketFactory      = StreamWebSocketFactory(logger = logProvider.taggedLogger("SCWebSocketFactory"))
val healthMonitor      = StreamHealthMonitor(logger = logProvider.taggedLogger("SCHealthMonitor"), scope = scope)

val batcher = StreamBatcher<String>(
    scope = scope,
    batchSize = 10,
    initialDelayMs = 100L,
    maxDelayMs = 1_000L,
)

val client = StreamClient(
    scope = scope,
    apiKey = apiKey,
    userId = userId,
    wsUrl = wsUrl,
    products = listOf("feeds"),
    clientInfoHeader = clientInfoHeader,
    tokenProvider = tokenProvider,
    logProvider = logProvider,
    clientSubscriptionManager = clientSubscriptionManager,
    tokenManager = tokenManager,
    singleFlight = singleFlight,
    serialQueue = serialQueue,
    retryProcessor = retryProcessor,
    connectionIdHolder = connectionIdHolder,
    socketFactory = socketFactory,
    healthMonitor = healthMonitor,
    batcher = batcher,
)
```

> **Gotcha:** don’t pass the **same** subscription/queue manager instance to both the client and a nested session. Keep ownership boundaries clear to avoid event recursion.

## Processing mechanisms

- **Serial Processing Queue**  
  Ordered, single-consumer coroutine pipeline. Backpressure is natural (FIFO).
  ```kotlin
  serialQueue.start()
  serialQueue.enqueue { /* work in order */ }
  serialQueue.stop()
  ```

- **Single-Flight Processor**  
  Coalesces concurrent calls with the same key into one in-flight job; callers await the same result.

- **Retry Processor**  
  Linear/exponential policy with `minRetries`, `maxRetries`, `initialDelayMillis`, optional `maxDelayMillis`, and a `giveUpFunction(attempt, Throwable)`.

- **Batcher**  
  Collects items into batches based on size and/or debounce window, then flushes on the queue/scope.

## Factories & default implementations
Public interfaces ship with convenience factory functions that return the default internal implementation (e.g., `StreamSerialProcessingQueue(...)` → `StreamSerialProcessingQueueImpl`). Prefer these factories in internal code; they keep call-sites stable while impls evolve. You can also provide custom implementations for testing or specialized behavior.

## License
Copyright (c) 2014-2025 Stream.io Inc.

Licensed under the Stream License; see [LICENSE](https://github.com/GetStream/stream-android-base/blob/main/LICENSE).  
Unless required by applicable law or agreed to in writing, software distributed under the License is provided **“as is”**, without warranties or conditions of any kind.
