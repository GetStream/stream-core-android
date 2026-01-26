# External Integrations

**Analysis Date:** 2026-01-26

## APIs & External Services

**Stream Backend (Primary):**
- Stream WebSocket API - Real-time connection for chat, feeds, video
  - SDK/Client: OkHttp 5.1.0 WebSocket client
  - Auth: JWT token via `StreamTokenProvider` interface
  - Location: `stream-android-core/src/main/java/io/getstream/android/core/internal/socket/factory/StreamWebSocketFactoryImpl.kt`

**REST API (Optional):**
- HTTP REST endpoints via Retrofit 3.0.0
  - SDK/Client: Retrofit with OkHttp backend
  - Auth: JWT Authorization header
  - Location: Managed via `StreamHttpConfig` and interceptors

## Data Storage

**Databases:**
- None - Library has NO persistent database integration
- Token caching: In-memory only (StateFlow in `StreamTokenManagerImpl.kt`)
- No Room, Realm, or SQLite integration

**File Storage:**
- None - No file system integration
- No local file caching or persistence

**Caching:**
- In-memory token cache: `cachedToken: MutableStateFlow<StreamToken?>` in `StreamTokenManagerImpl.kt`
- HTTP client configuration supports OkHttp cache layer (consumer app configures)
- Message batching cache: `StreamBatcher` (10 items, in-memory)
- Rewatch registry: `ConcurrentHashMap` in `StreamWatcherImpl.kt` (in-memory)

## Authentication & Identity

**Auth Provider:**
- Custom implementation required by consumer
  - Interface: `StreamTokenProvider` located at `stream-android-core/src/main/java/io/getstream/android/core/api/authentication/StreamTokenProvider.kt`
  - Contract: Implement `suspend fun loadToken(userId: StreamUserId): StreamToken`
  - Responsibility: Network call to consumer's auth backend to mint JWT tokens
  - Never embed secrets on-device; token creation/rotation must happen on server

**Token Management:**
- Implementation: `StreamTokenManagerImpl.kt`
- Single-flight pattern: Deduplicates concurrent refresh requests
- Invalidation on error codes: 40 (invalid signature), 41 (expired), 42 (revoked)
- Caching strategy: Caches first token, invalidates on error, refreshes on demand
- Interceptor integration: `StreamAuthInterceptor` handles token refresh in HTTP layer

**WebSocket Authentication Flow:**
1. Opens WebSocket with API key: `?api_key=<key>&stream-auth-type=jwt`
2. Receives HTTP 101 upgrade response
3. Sends `StreamWSAuthMessageRequest` with JWT token and user details
4. Receives `StreamClientConnectedEvent` (type: "connection.ok") on success
5. Receives `StreamClientConnectionErrorEvent` (type: "connection.error") on failure

## Monitoring & Observability

**Error Tracking:**
- None - Library provides no built-in error tracking integration (Sentry, Firebase Crashlytics, etc.)
- Consumer app responsible for integrating error tracking
- Errors surfaced as: `Result<T>` return types with `Throwable` in failure case
- API errors: `StreamEndpointException` wraps backend error responses

**Logs:**
- Approach: Custom logger interface via `StreamLogger` and `StreamLoggerProvider`
- Default implementation: Android `Log` class (LogCat)
- Configuration: `StreamLoggerProvider.defaultAndroidLogger()` factory
- Log levels: Verbose, Debug, Info, Warning, Error
- Chunking: Logs split if exceeding 4000 characters (LogCat limit)
- Location: `stream-android-core/src/main/java/io/getstream/android/core/api/log/`

**Health Monitoring:**
- Internal: `StreamHealthMonitor` - Periodic heartbeat (25s interval, 60s timeout)
- Connection state: `connectionState: StateFlow<StreamConnectionState>` (hot state flow)
- Network state: `StreamNetworkMonitor` observes `ConnectivityManager` callbacks
- Lifecycle state: `StreamLifecycleMonitor` observes app background/foreground via `ProcessLifecycleOwner`

## CI/CD & Deployment

**Hosting:**
- None configured - Library for integration into Android applications
- Stream backend: Consumer app connects to Stream cloud services

**CI Pipeline:**
- Detekt configured for auto-correcting code quality issues
- Spotless for code formatting (ktfmt)
- Tests run via Gradle: `./gradlew :stream-android-core:test`
- Lint checks enforced: `abortOnError = true, warningsAsErrors = true`

**Publishing:**
- Maven Central publishing via stream-conventions plugin
- Repository: `stream-core-android`
- Uses `com.vanniktech.maven.publish` for publishing configuration
- Requires: Maven signing credentials for publish

## Environment Configuration

**Required env vars:**
- None configured at library level
- Consumer app must provide:
  - `StreamApiKey` - API key for Stream backend
  - `StreamWsUrl` - WebSocket URL (wss://...)
  - `StreamTokenProvider` implementation - Token fetching logic
  - Android `Context` - For system service access

**Secrets location:**
- Token provider responsibility: Secure token minting on consumer's backend
- API key: Passed to StreamClient at initialization
- No .env or secrets.json integration in library

## HTTP Interceptor Chain

**Request Order (Outgoing):**

1. **StreamApiKeyInterceptor** - Adds `?api_key=<key>` query parameter
   - Location: `stream-android-core/src/main/java/io/getstream/android/core/internal/http/interceptor/StreamApiKeyInterceptor.kt`

2. **StreamAuthInterceptor** - Adds JWT authorization header
   - Location: `stream-android-core/src/main/java/io/getstream/android/core/internal/http/interceptor/StreamAuthInterceptor.kt`
   - Behavior: Synchronous token loading, automatic single retry with fresh token on codes 40/41/42
   - Uses `runBlocking` for token operations (OkHttp constraint)

3. **StreamClientInfoInterceptor** - Adds `X-Stream-Client: <version>/<platform>` header
   - Location: `stream-android-core/src/main/java/io/getstream/android/core/internal/http/interceptor/StreamClientInfoInterceptor.kt`

4. **StreamConnectionIdInterceptor** - Adds `?connection_id=<id>` query parameter if available
   - Location: `stream-android-core/src/main/java/io/getstream/android/core/internal/http/interceptor/StreamConnectionIdInterceptor.kt`

5. **StreamEndpointErrorInterceptor** - Parses error responses into exceptions
   - Location: `stream-android-core/src/main/java/io/getstream/android/core/internal/http/interceptor/StreamEndpointErrorInterceptor.kt`
   - Throws `StreamEndpointException` with parsed error data

**Configuration:**
- Location: `stream-android-core/src/main/java/io/getstream/android/core/api/model/config/StreamHttpConfig.kt`
- Enable/disable automatic interceptors: `automaticInterceptors: Boolean = true`
- Custom interceptors: `configuredInterceptors: Set<Interceptor>`
- HTTP client builder: `OkHttpClient.Builder` for customization

## Webhooks & Callbacks

**Incoming:**
- WebSocket events: All incoming events parsed via `StreamCompositeEventSerializationImpl`
- Core events: `StreamClientConnectedEvent` (connection.ok), `StreamClientConnectionErrorEvent` (connection.error), `StreamHealthCheckEvent` (heartbeat)
- Product events: Custom event types handled by consumer-provided serializers

**Event Distribution:**
- Location: `stream-android-core/src/main/java/io/getstream/android/core/api/subscribe/`
- Interface: `StreamClientListener` - Implement to receive socket events
- Methods:
  - `onState(state: StreamConnectionState)` - Connection state changes
  - `onEvent(event: Any)` - Incoming socket events
  - `onError(err: Throwable)` - Error notifications
  - `onRecovery(recovery: Recovery)` - Recovery decision notifications
- Subscription model: `StreamSubscriptionManager<StreamClientListener>` with retention options (auto-remove or keep-until-cancelled)

**Outgoing (Rewatch on Reconnect):**
- Interface: `StreamRewatchListener<T>` in `stream-android-core/src/main/java/io/getstream/android/core/api/watcher/StreamRewatchListener.kt`
- Callback: `suspend fun onRewatch(items: List<T>, connectionId: String)`
- Triggered: On every `StreamConnectionState.Connected` transition
- Used by: `StreamWatcher<T>` to re-establish server-side watches after reconnection
- Managed by: Product SDKs (Chat, Video, Feeds) to re-watch channels/conversations

**Android System Callbacks:**
- `ConnectivityManager.NetworkCallback` - Network state changes
- `ProcessLifecycleOwner.Lifecycle` observer - App background/foreground transitions
- Both monitored by `StreamNetworkMonitor` and `StreamLifecycleMonitor`

## Event Serialization

**JSON Framework:** Moshi 1.15.2

**Code Generation:**
- KSP generates Moshi adapters at compile time
- Pattern: `@JsonClass(generateAdapter = true)` on data classes
- Models location: `stream-android-core/src/main/java/io/getstream/android/core/api/model/`
- Internal models location: `stream-android-core/src/main/java/io/getstream/android/core/internal/model/`

**Event Types:**

Core Events (Handled by library):
- `StreamClientConnectedEvent` - type: "connection.ok" - Successful authentication
- `StreamClientConnectionErrorEvent` - type: "connection.error" - Auth failure
- `StreamHealthCheckEvent` - type: "health.check" - Periodic heartbeat echo

Product Events:
- Delegated to consumer-provided serializers
- Registered via `StreamClientSerializationConfig.productEventSerializers`

**Composite Serialization:**
- Location: `stream-android-core/src/main/java/io/getstream/android/core/internal/serialization/StreamCompositeEventSerializationImpl.kt`
- Purpose: Route events to appropriate deserializers based on "type" field
- Implementation: Peeks JSON "type" field, routes to core or product parser

## Error Handling & Codes

**Error Types:**

- `StreamEndpointException` - API errors from backend
  - Contains: `code: Int, message: String?, apiError: StreamEndpointErrorData`
  - Token error codes: 40 (invalid signature), 41 (expired), 42 (revoked)
  - Location: `stream-android-core/src/main/java/io/getstream/android/core/api/model/exceptions/StreamEndpointException.kt`

- `StreamClientException` - Client-level errors
  - Socket health failure: "Socket did not receive any events."
  - Message drop: "Failed to offer message to debounce processor"

- `IOException` - Socket closure
  - Contains: `"Socket closed. Code: $code, Reason: $reason"`

**Error Extension:**
- `Result<T>.onTokenError()` - Detects and handles token errors in `Result` type
- Location: `stream-android-core/src/main/java/io/getstream/android/core/internal/client/StreamClientImpl.kt`

---

*Integration audit: 2026-01-26*
