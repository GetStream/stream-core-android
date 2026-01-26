# Codebase Structure

**Analysis Date:** 2026-01-26

## Directory Layout

```
stream-android-core/
├── src/
│   ├── main/
│   │   ├── java/io/getstream/android/core/
│   │   │   ├── api/                      # Public API contracts (interfaces, data classes)
│   │   │   │   ├── StreamClient.kt       # Main entry point interface + factory function
│   │   │   │   ├── authentication/       # Token manager, token provider interfaces
│   │   │   │   ├── components/           # Android components provider
│   │   │   │   ├── filter/               # Filter builder interfaces (if applicable)
│   │   │   │   ├── http/                 # OkHttp interceptor builders
│   │   │   │   ├── log/                  # Logger provider, logger interface
│   │   │   │   ├── model/                # Data classes (connection, errors, config, events)
│   │   │   │   ├── observers/            # Monitor interfaces (network, lifecycle)
│   │   │   │   ├── processing/           # Processor interfaces (batcher, queue, retry, single-flight)
│   │   │   │   ├── recovery/             # Connection recovery evaluator interface
│   │   │   │   ├── serialization/        # Serialization interfaces
│   │   │   │   ├── socket/               # Socket, WebSocket, health monitor interfaces
│   │   │   │   ├── sort/                 # Sort builder interfaces (if applicable)
│   │   │   │   ├── subscribe/            # Subscription manager, listener interfaces
│   │   │   │   ├── utils/                # Extension functions, utility functions
│   │   │   │   └── watcher/              # Watcher interface, listener interface
│   │   │   └── internal/                 # Internal implementations (NOT exposed)
│   │   │       ├── client/               # StreamClientImpl - main client implementation
│   │   │       ├── authentication/       # StreamTokenManagerImpl
│   │   │       ├── components/           # Android component instantiation
│   │   │       ├── filter/               # Filter implementation (if applicable)
│   │   │       ├── http/                 # HTTP interceptor implementations
│   │   │       ├── model/                # Internal-only data classes
│   │   │       │   ├── authentication/   # Auth request/response models
│   │   │       │   ├── events/           # Core event types (connection.ok, health.check)
│   │   │       │   └── network/          # Network request models
│   │   │       ├── observers/            # Monitor implementations
│   │   │       │   ├── lifecycle/        # StreamLifecycleMonitorImpl
│   │   │       │   └── network/          # StreamNetworkMonitorImpl
│   │   │       ├── processing/           # Processor implementations
│   │   │       │   ├── StreamBatcherImpl.kt
│   │   │       │   ├── StreamSerialProcessingQueueImpl.kt
│   │   │       │   ├── StreamSingleFlightProcessorImpl.kt
│   │   │       │   ├── StreamRetryProcessorImpl.kt
│   │   │       │   └── StreamRestartableChannel.kt
│   │   │       ├── recovery/             # StreamConnectionRecoveryEvaluatorImpl
│   │   │       ├── serialization/        # Serialization implementations
│   │   │       │   ├── StreamCompositeEventSerializationImpl.kt
│   │   │       │   ├── StreamCompositeMoshiJsonSerialization.kt
│   │   │       │   ├── StreamMoshiJsonSerializationImpl.kt
│   │   │       │   └── moshi/            # Moshi provider and custom adapters
│   │   │       ├── socket/               # Socket and WebSocket implementations
│   │   │       │   ├── StreamSocketSession.kt
│   │   │       │   ├── StreamWebSocketImpl.kt
│   │   │       │   ├── SocketConstants.kt
│   │   │       │   ├── connection/       # Connection models (internal)
│   │   │       │   ├── factory/          # WebSocket factory implementation
│   │   │       │   ├── model/            # Socket-specific models (ConnectUserData)
│   │   │       │   └── monitor/          # StreamHealthMonitorImpl
│   │   │       ├── sort/                 # Sort implementation (if applicable)
│   │   │       ├── subscribe/            # Subscription manager implementation
│   │   │       ├── watcher/              # StreamWatcherImpl - watch registry
│   │   │       └── (files)               # Direct imports
│   │   └── res/                          # Android resources (manifests, etc)
│   ├── test/                             # Unit tests
│   │   └── java/io/getstream/android/core/
│   │       └── internal/                 # Test implementations mirror internal/
│   │           ├── watcher/              # StreamWatcherImplTest.kt
│   │           ├── processing/           # Processor tests
│   │           ├── recovery/             # Recovery evaluator tests
│   │           ├── serialization/        # Serialization tests
│   │           ├── subscribe/            # Subscription manager tests
│   │           ├── http/                 # Interceptor tests
│   │           ├── socket/               # Socket session, health monitor tests
│   │           ├── authentication/       # Token manager tests
│   │           ├── client/               # Client implementation tests
│   │           └── observers/            # Monitor tests
│   └── androidTest/                      # Instrumented tests (Android-specific)
│       └── java/io/getstream/android/core/
├── build.gradle.kts                      # Gradle build configuration
├── proguard-rules.pro                    # ProGuard obfuscation rules
└── consumer-rules.pro                    # Consumer-facing ProGuard rules

stream-android-core-annotations/          # Annotations module
├── src/main/java/io/getstream/android/core/annotations/
│   ├── StreamPublishedApi.kt              # Stable public API marker
│   ├── StreamInternalApi.kt               # Internal API marker
│   └── StreamDelicateApi.kt               # Advanced/dangerous API marker

stream-android-core-lint/                 # Custom Lint checks
├── src/main/java/io/getstream/android/core/lint/detectors/
└── src/test/java/io/getstream/android/core/lint/detectors/

app/                                       # Demo application (if applicable)
├── src/main/java/
├── src/test/java/
└── src/androidTest/java/
```

## Directory Purposes

**api/ (Public contracts):**
- Purpose: All interfaces, enums, data classes exposed to product SDKs
- Contains: Stable contracts that should not break between minor versions
- Key files: `StreamClient.kt`, `StreamConnectionState.kt`, `StreamWatcher.kt`

**api/model/ (Data classes):**
- Purpose: Data transfer objects and value objects for serialization
- Contains: Connection state, user data, error data, configuration
- Key files: `ConnectionState.kt`, `ConnectedUser.kt`, `StreamEndpointErrorData.kt`, `StreamRetryPolicy.kt`

**api/processing/ (Processor interfaces):**
- Purpose: Interfaces for async work coordination
- Contains: Batcher, retry processor, single-flight, serial queue
- Key files: `StreamBatcher.kt`, `StreamSerialProcessingQueue.kt`, `StreamSingleFlightProcessor.kt`

**api/socket/ (Socket interfaces):**
- Purpose: WebSocket abstraction layer
- Contains: WebSocket interface, factory, health monitor interface
- Key files: `StreamWebSocket.kt`, `StreamWebSocketFactory.kt`, `StreamHealthMonitor.kt`

**api/subscribe/ (Subscription interfaces):**
- Purpose: Listener registration and event dispatch
- Contains: Subscription manager, observable, subscription interface
- Key files: `StreamSubscriptionManager.kt`, `StreamObservable.kt`

**api/serialization/ (Serialization contracts):**
- Purpose: Event and JSON serialization abstractions
- Contains: Event deserializer, JSON serialization interfaces
- Key files: `StreamEventSerialization.kt`, `StreamJsonSerialization.kt`

**api/watcher/ (Watch registry):**
- Purpose: Generic watch management for connection-aware resource tracking
- Contains: Watcher interface, rewatch listener callback
- Key files: `StreamWatcher.kt`, `StreamRewatchListener.kt`

**internal/client/ (Client implementation):**
- Purpose: Main client implementation coordinating all components
- Contains: `StreamClientImpl` - orchestrates socket, monitors, processors
- Key files: `StreamClientImpl.kt`

**internal/socket/ (WebSocket layer):**
- Purpose: WebSocket lifecycle, event batching, authentication
- Contains: Session manager, socket wrapper, health checks, event routing
- Key files: `StreamSocketSession.kt`, `StreamWebSocketImpl.kt`, `StreamHealthMonitorImpl.kt`

**internal/processing/ (Concurrency utilities):**
- Purpose: Backpressure, deduplication, batching, sequential execution
- Contains: All processor implementations
- Key files: `StreamBatcherImpl.kt`, `StreamSingleFlightProcessorImpl.kt`, `StreamSerialProcessingQueueImpl.kt`

**internal/serialization/ (Event/JSON handling):**
- Purpose: Deserialize WebSocket events, route to handlers
- Contains: Composite serialization, Moshi integration
- Key files: `StreamCompositeEventSerializationImpl.kt`, `StreamMoshiJsonSerializationImpl.kt`

**internal/authentication/ (Token management):**
- Purpose: JWT lifecycle, refresh logic, caching
- Contains: Token manager implementation
- Key files: `StreamTokenManagerImpl.kt`

**internal/observers/ (Monitoring):**
- Purpose: Track network connectivity and app lifecycle
- Contains: Network monitor, lifecycle monitor, aggregated monitor
- Key files: `StreamNetworkMonitorImpl.kt`, `StreamLifecycleMonitorImpl.kt`

**internal/watcher/ (Watch registry implementation):**
- Purpose: Maintain watched resources, trigger re-watches on reconnection
- Contains: Generic watcher implementation
- Key files: `StreamWatcherImpl.kt`

## Key File Locations

**Entry Points:**
- `stream-android-core/src/main/java/io/getstream/android/core/api/StreamClient.kt`: Factory function, main interface
- `stream-android-core/src/main/java/io/getstream/android/core/api/watcher/StreamWatcher.kt`: Watch management factory function

**Configuration:**
- `build.gradle.kts`: Gradle build, dependencies, KSP setup
- `stream-android-core/src/main/AndroidManifest.xml`: Android manifest (permissions)
- `lint.xml`: Android lint configuration

**Core Logic:**
- `stream-android-core/src/main/java/io/getstream/android/core/internal/client/StreamClientImpl.kt`: Client orchestration
- `stream-android-core/src/main/java/io/getstream/android/core/internal/socket/StreamSocketSession.kt`: Socket lifecycle
- `stream-android-core/src/main/java/io/getstream/android/core/internal/socket/StreamWebSocketImpl.kt`: WebSocket wrapper

**Authentication:**
- `stream-android-core/src/main/java/io/getstream/android/core/internal/authentication/StreamTokenManagerImpl.kt`: Token lifecycle
- `stream-android-core/src/main/java/io/getstream/android/core/internal/http/interceptor/StreamAuthInterceptor.kt`: JWT injection

**Processing:**
- `stream-android-core/src/main/java/io/getstream/android/core/internal/processing/StreamBatcherImpl.kt`: Message batching
- `stream-android-core/src/main/java/io/getstream/android/core/internal/processing/StreamSerialProcessingQueueImpl.kt`: Sequential execution
- `stream-android-core/src/main/java/io/getstream/android/core/internal/processing/StreamSingleFlightProcessorImpl.kt`: Deduplication

**Testing:**
- `stream-android-core/src/test/java/io/getstream/android/core/`: Unit test root (mirrors internal structure)
- `stream-android-core/src/test/java/io/getstream/android/core/internal/watcher/StreamWatcherImplTest.kt`: Watcher tests

## Naming Conventions

**Files:**
- Impl classes: `StreamXyzImpl.kt` (e.g., `StreamTokenManagerImpl.kt`)
- Test classes: `<ClassUnderTest>Test.kt` (e.g., `StreamRetryProcessorImplTest.kt`)
- Interface/factory files: `StreamXyz.kt` (e.g., `StreamClient.kt`, `StreamWatcher.kt`)
- Internal models: `Stream<DomainObject>Request.kt`, `Stream<DomainObject>Response.kt` (e.g., `StreamWSAuthMessageRequest.kt`)

**Directories:**
- Internal package: `internal/` - implementation details not exposed
- API package: `api/` - public stable contracts
- Component categories: lowercase plural (e.g., `socket/`, `processing/`, `observers/`)
- Feature grouping: `<feature>/` (e.g., `authentication/`, `serialization/`, `http/`)

**Classes:**
- Public interfaces: `Stream<Name>` (e.g., `StreamClient`, `StreamBatcher`)
- Implementations: `Stream<Name>Impl` (e.g., `StreamBatcherImpl`)
- Event classes: `Stream<Event>Event` (e.g., `StreamClientConnectedEvent`, `StreamHealthCheckEvent`)
- Request/Response: `Stream<Model><Type>Request/Response` (e.g., `StreamWSAuthMessageRequest`)

**Functions/Methods:**
- camelCase: `connect()`, `disconnect()`, `reconnect()`
- Factory functions: Top-level functions matching interface name (e.g., `StreamClient()` factory)
- Verb-based: `start()`, `stop()`, `watch()`, `subscribe()`, `offer()`

**Variables:**
- camelCase: `connectionState`, `tokenManager`, `healthMonitor`
- Private fields: Prefix with underscore for clarity (e.g., `_buffer`, `_state`)
- Constants: `UPPER_SNAKE_CASE` in `companion object` or top-level

## Where to Add New Code

**New Feature (e.g., new event type):**
- Event class definition: `stream-android-core/src/main/java/io/getstream/android/core/internal/model/events/Stream<Feature>Event.kt`
- Public API if needed: `stream-android-core/src/main/java/io/getstream/android/core/api/model/...`
- Event handling: Update `StreamCompositeEventSerializationImpl.kt` routing if needed
- Tests: `stream-android-core/src/test/java/io/getstream/android/core/internal/model/events/Stream<Feature>EventTest.kt`

**New Component/Module (e.g., new monitor):**
- Interface: `stream-android-core/src/main/java/io/getstream/android/core/api/<category>/Stream<Name>.kt`
- Implementation: `stream-android-core/src/main/java/io/getstream/android/core/internal/<category>/Stream<Name>Impl.kt`
- Register in: `StreamClient` factory function if it's a dependency
- Tests: `stream-android-core/src/test/java/io/getstream/android/core/internal/<category>/Stream<Name>ImplTest.kt`

**Utilities/Extensions:**
- Shared helpers: `stream-android-core/src/main/java/io/getstream/android/core/api/utils/`
- Internal helpers: `stream-android-core/src/main/java/io/getstream/android/core/internal/` (in same package as usage)
- Extension functions: Top-level in same file or in `utils/` package
- Tests: Co-located with implementation file using `Test` suffix

**Data Models:**
- Public models: `stream-android-core/src/main/java/io/getstream/android/core/api/model/<domain>/`
- Internal models: `stream-android-core/src/main/java/io/getstream/android/core/internal/model/<domain>/`
- Use `@JsonClass(generateAdapter = true)` for Moshi serialization

**HTTP Interceptors:**
- Location: `stream-android-core/src/main/java/io/getstream/android/core/internal/http/interceptor/Stream<Name>Interceptor.kt`
- Add to chain: Update `StreamClient.kt` factory function (lines 317-330) to add interceptor to builder
- Tests: `stream-android-core/src/test/java/io/getstream/android/core/internal/http/interceptor/Stream<Name>InterceptorTest.kt`

## Special Directories

**annotations/ (Visibility annotations):**
- Purpose: Mark APIs with stability guarantees
- Generated: No - hand-written annotations
- Committed: Yes - part of source tree
- Files: `StreamPublishedApi.kt`, `StreamInternalApi.kt`, `StreamDelicateApi.kt`

**build/ (Build outputs):**
- Purpose: Compiled classes, resources, AAR, test reports
- Generated: Yes - from gradle build
- Committed: No - .gitignored
- Location: `stream-android-core/build/`

**.gradle/ (Gradle cache):**
- Purpose: Gradle build cache and metadata
- Generated: Yes - by gradle daemon
- Committed: No - .gitignored
- Location: Project root and module-level

**config/ (Build configuration):**
- Purpose: Detekt rules, lint configuration
- Generated: No - hand-written
- Committed: Yes
- Location: `config/detekt/detekt.yml`

**buildSrc/ (Build logic):**
- Purpose: Gradle plugins and version management
- Generated: No - hand-written
- Committed: Yes
- Location: `buildSrc/src/main/kotlin/`

---

*Structure analysis: 2026-01-26*
