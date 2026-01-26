# Codebase Concerns

**Analysis Date:** 2026-01-26

## Tech Debt

### OkHttp Thread Pool Blocking (High Priority)

**Issue:** `StreamAuthInterceptor` uses `runBlocking` to load and refresh tokens on OkHttp's synchronous HTTP thread.

**Files:** `stream-android-core/src/main/java/io/getstream/android/core/internal/http/interceptor/StreamAuthInterceptor.kt:66-67, 91-92`

**Impact:**
- Token provider `loadToken()` and `refresh()` suspending functions block the OkHttp thread pool
- Long-running token operations (network delays, slow provider implementations) freeze HTTP request processing
- Multiple concurrent HTTP requests face cascading delays waiting for token availability
- Potential for thread starvation if token provider is slow

**Context:** OkHttp interceptors are inherently synchronous; retrofit/HTTP layer cannot use suspend functions. This is a fundamental architectural constraint (lines 44).

**Fix Approach:**
- Keep synchronous call sites as-is (unavoidable)
- Add performance guidance in token provider contract: require `TokenProvider.loadToken()` to complete in <100ms
- Implement token pre-refresh: call `tokenManager.refresh()` before token expiry via scheduled background task
- Cache tokens aggressively to avoid blocking on load operations
- Monitor token provider latency in production with metrics
- Document that slow token providers directly degrade HTTP performance

---

### Uninitialized Property Risk in StreamWebSocketImpl

**Issue:** `StreamWebSocketImpl` uses `lateinit var socket: WebSocket` initialized only in `open()` method.

**Files:** `stream-android-core/src/main/java/io/getstream/android/core/internal/socket/StreamWebSocketImpl.kt:39`

**Impact:**
- Calling `send()` or `close()` before `open()` throws `UninitializedPropertyAccessException`
- Runtime crash instead of graceful error
- Tests may pass with mocked sockets but fail in production where initialization order matters

**Current Mitigation:** `withSocket` helper checks `::socket.isInitialized` (line 135) with clear error message

**Fix Approach:**
- Add explicit validation in public methods: throw early with documented error message
- Current implementation is defensive (lines 138-140); no immediate action required
- Document initialization contract in `open()` docstring

---

### Nullable Field Assignment Without Synchronization

**Issue:** `StreamHealthMonitorImpl.lastAck` is volatile-equivalent but assigned unsafely across threads.

**Files:** `stream-android-core/src/main/java/io/getstream/android/core/internal/socket/monitor/StreamHealthMonitorImpl.kt:52, 67`

**Impact:**
- Race condition if `acknowledgeHeartbeat()` (line 67) called from OkHttp WebSocket thread while `start()` runs health check logic on scope thread
- Lost heartbeat acknowledgment, falsely triggering liveness threshold
- Stale `lastAck` value causing premature socket disconnection

**Current Behavior:** Simple `Long` assignment is atomic on JVM, but memory visibility not guaranteed across threads without volatile

**Fix Approach:**
- Declare `lastAck` as `AtomicLong` instead of plain `Long`
- Use `AtomicLong.getAndSet()` in `acknowledgeHeartbeat()` for true atomicity with visibility guarantee
- Match pattern used in `StreamSocketSession` (lines 68, 71)

---

## Known Bugs

### Race Condition in StreamSocketSession Message Handling

**Issue:** No guaranteed ordering when both `onMessage` callbacks (eventListener and temporary handshakeListener) receive same frame from WebSocket.

**Files:**
- `stream-android-core/src/main/java/io/getstream/android/core/internal/socket/StreamSocketSession.kt:82-120` (persistent listener)
- `stream-android-core/src/main/java/io/getstream/android/core/internal/socket/StreamSocketSession.kt:369-415` (handshake listener)

**Trigger:** WebSocket message arrives while both listeners are subscribed (lines 314, 444).

**Impact:**
- Message processed by temporary handshake listener first (priority unclear)
- If batch processing in `eventListener` causes async processing, state becomes inconsistent
- Connection success callback may not execute if handshake listener consumes message first

**Symptoms:**
- Sporadic connect failures on high-latency networks
- Flaky tests under concurrent WebSocket events

**Workaround:** Temporary listeners explicitly cancel before permanent ones take over (line 286)

**Fix Approach:**
- Replace subscription model with explicit handshake state machine
- Reject incoming messages during handshake phase until connection completes
- Add message ordering guarantee in subscription manager or use sequential listener dispatch

---

### Missing Error Handling in Batcher Message Drop

**Issue:** When `batcher.offer()` fails (channel full), socket disconnects but no detailed logging of buffer state.

**Files:** `stream-android-core/src/main/java/io/getstream/android/core/internal/socket/StreamSocketSession.kt:87-94`

**Impact:**
- Server sends rapid event burst; batch buffer capacity exceeded
- Socket silently disconnects with generic "Message dropped" error
- No visibility into which messages were lost or why buffer is full
- Difficult to diagnose production issues related to high-frequency events

**Current Behavior:** Logs message content but not buffer size, pending job count, or heap pressure

**Fix Approach:**
- Log buffer fill percentage and pending batch size before disconnect
- Add metrics/counters for drop events
- Increase default `channelCapacity` if burst handling inadequate
- Consider circuit breaker pattern: slow down message processing instead of dropping

---

## Security Considerations

### Token Serialization in Logs (Medium Risk)

**Issue:** Token values appear in debug/verbose logs when serializing authentication requests.

**Files:**
- `stream-android-core/src/main/java/io/getstream/android/core/internal/socket/StreamSocketSession.kt:348` (auth request)
- `stream-android-core/src/main/java/io/getstream/android/core/internal/client/StreamClientImpl.kt:194` (token error)

**Impact:**
- Debug logs containing full JWT tokens could be exposed in crash reports, monitoring systems, or device logs
- Token replay attack risk if logs are captured/exfiltrated
- Compliance issue: tokens should never appear in plaintext logs

**Current Mitigation:** Uses lambda-based logging (`{ "..." }`) which only executes when log level enabled; still risky at DEBUG level

**Recommendations:**
- Implement token redaction utility: replace full token with `<token_hash>` in serialization
- Mark `StreamToken` data class with custom `toString()` that redacts value
- Add pre-commit hook to flag token-related debug logs
- Disable verbose socket logging in production builds

---

### Connection ID Holder Race Condition

**Issue:** `StreamConnectionIdHolder` may have unsynchronized access during rapid connect/disconnect cycles.

**Files:** `stream-android-core/src/main/java/io/getstream/android/core/api/socket/StreamConnectionIdHolder.kt`

**Impact:**
- Race between `setConnectionId()` (line 146 in StreamClientImpl) and `clear()` (line 163)
- Stale connection ID leaks into subsequent HTTP requests via interceptor
- Security: requests authenticated to wrong connection context
- User A's requests may be processed in User B's connection context

**Current Protection:** Atomic operations (AtomicReference), but compound operations not atomic

**Fix Approach:**
- Wrap compound operations in synchronized block: `setConnectionId()` + `clear()` both acquire same lock
- Ensure interceptor reads connection ID safely with consistent snapshot
- Add test: concurrent set/clear/get operations verify final state consistency

---

## Performance Bottlenecks

### Adaptive Window Backoff May Cause Cascading Delays

**Issue:** `StreamBatcher` doubles delay window on full batch, potentially causing 1s+ delays in high-traffic scenarios.

**Files:** `stream-android-core/src/main/java/io/getstream/android/core/internal/processing/StreamBatcherImpl.kt:120-126`

**Impact:**
- Server sends 10+ events per batch
- Window doubles: 100ms → 200ms → 400ms → 1000ms (max)
- Realtime features feel sluggish during burst traffic
- User perceives lag in chat message appearance, notification delivery

**Current Values:** `initialDelayMs=100, maxDelayMs=1000` (lines 31-33, config in CLAUDE.md)

**Scenario:** Mobile app reconnects after losing network; server queues 100 events; batches process slowly due to backoff

**Fix Approach:**
- Reduce `maxDelayMs` to 500ms for chat/messaging workloads
- Add exponential fallback: if traffic sustained, cap at lower threshold
- Profile typical event frequencies; tune window to match expected patterns
- Consider predictive backoff: ramp up based on event velocity, not just batch full status

---

### Health Monitor Check Granularity

**Issue:** Health check every 25s with 60s timeout means 35s window for detecting dead connections.

**Files:** `stream-android-core/src/main/java/io/getstream/android/core/internal/socket/monitor/StreamHealthMonitorImpl.kt:47-48`

**Impact:**
- Dead socket (server-side) not detected for up to 60+ seconds
- Users see frozen UI during this window
- Production: significant negative impact on perceived app reliability

**Trade-off:** More frequent checks increase battery/bandwidth; less frequent increases stale connection window

**Fix Approach:**
- Consider 10s health checks with 30s timeout for critical realtime features
- Make configurable: allow SDKs to tune for their latency tolerance
- Add jitter to health checks: prevent thundering herd on reconnect

---

## Fragile Areas

### Token Manager Refresh Deduplication

**Issue:** Token refresh relies on `StreamSingleFlightProcessor` for deduplication. If processor is cleared/restarted unexpectedly, multiple concurrent refreshes may occur.

**Files:**
- `stream-android-core/src/main/java/io/getstream/android/core/internal/authentication/StreamTokenManagerImpl.kt:58-63`
- `stream-android-core/src/main/java/io/getstream/android/core/internal/processing/StreamSingleFlightProcessorImpl.kt:41-82`

**Fragility:** `StreamClientImpl.disconnect()` calls `singleFlight.clear(true)` (line 172), which clears the processor. If `refresh()` is in-flight, continuation is abandoned.

**Impact:**
- Token provider called multiple times for same refresh
- Backend may rate-limit token endpoint
- Cascade: rate limit leads to connect failure

**Safe Modification:**
- Never clear processor while token refresh in-flight
- Check `singleFlight.has(refreshKey)` before clearing
- Add timeout to token refresh: fail instead of hanging forever

---

### Socket Session Cleanup Idempotency

**Issue:** `cleanup()` uses `AtomicBoolean` to guard idempotent shutdown, but `disconnect()` called again after cleanup may not notify listeners properly.

**Files:** `stream-android-core/src/main/java/io/getstream/android/core/internal/socket/StreamSocketSession.kt:464-474`

**Impact:**
- First `disconnect()` calls `notifyState(Disconnected)` via `closingByUs` guard (line 147)
- Second `disconnect()` skips state notification (already set)
- Listeners don't receive final cleanup signal
- Resource leaks: subscriptions not cancelled if already cleaned

**Safe Modification:**
- Document that `disconnect()` is idempotent for state, but socket close attempted every time
- Add explicit comment: "listener notification only on first call"
- Test concurrent `disconnect()` calls verify single notification

---

## Scaling Limits

### Message Batch Buffer Size

**Current Capacity:** `Channel.UNLIMITED` default for batcher inbox (line 35 in StreamBatcherImpl)

**Limit Scenario:**
- Server sends 10,000 events in rapid succession
- Each event 1KB → 10MB buffered in memory
- On memory-constrained devices (low-end Android), OutOfMemoryError
- Buffer never cleared if consumer paused/blocked

**Impact:** High-end devices + heavy usage (video calls with many participants) can hit memory ceiling

**Fix Approach:**
- Change default to `Channel.BUFFERED` (65-item buffer)
- Provide configurable `channelCapacity` parameter for SDKs
- Add backpressure: slow consumer signals upstream, prevents accept at source
- Monitor buffer depth; log warnings at 80%/90% thresholds

---

### Network Monitor Callback Dispatch

**Issue:** `StreamNetworkAndLifeCycleMonitor` dispatches state changes synchronously to all listeners.

**Files:** `stream-android-core/src/main/java/io/getstream/android/core/internal/observers/StreamNetworkAndLifecycleMonitorListener.kt`

**Impact:**
- One slow listener blocks all others from receiving network state
- Example: SDKs with heavy recovery logic block network monitor callbacks
- Cascading effect: app-wide UI freezes during network transitions

**Fix Approach:**
- Dispatch to listeners asynchronously (launch on scope)
- Add timeout: cancel listener callback after 5s, log warning
- Profile typical listener callback time; set SLA expectations

---

## Test Coverage Gaps

### StreamClientImpl Integration Testing

**Files:** `stream-android-core/src/main/java/io/getstream/android/core/internal/client/StreamClientImpl.kt`

**What's Not Tested:**
- Full connect → disconnect → reconnect cycle with network/lifecycle state changes
- Token refresh during in-flight connect (lines 193-200)
- Race between `connect()` and `disconnect()` via single-flight processor
- Recovery effect execution with concurrent state changes (lines 203-241)

**Risk:** Medium - Core client logic may have race conditions not caught by unit tests

**Recommendation:**
- Add integration test: simulate network loss during authentication
- Add stress test: rapid connect/disconnect/recover cycles (50+ iterations)
- Use `TestDispatchers` to control timing and expose race conditions

---

### StreamSocketSession Authentication Handshake

**Files:** `stream-android-core/src/main/java/io/getstream/android/core/internal/socket/StreamSocketSession.kt:182-462`

**What's Not Tested:**
- WebSocket closes during authentication (onClosed callback while Authenticating state)
- Message arrives after handshake timeout but before cancellation
- Connection success followed by immediate server error event
- Health check callback throws exception (lines 211-228)

**Risk:** High - Complex state machine with timing-dependent behavior

**Recommendation:**
- Test: WebSocket closes during Authenticating state → verify Disconnected emitted with cause
- Test: Delayed auth response → verify timeout handling
- Test: Health check callback exception → verify monitor continues monitoring

---

### Network/Lifecycle Monitor Snapshot Atomicity

**Files:** `stream-android-core/src/main/java/io/getstream/android/core/internal/observers/`

**What's Not Tested:**
- Rapid network state → background → foreground transitions
- Snapshot consistency: network state + lifecycle state read atomically (lines 121-127 in StreamClientImpl)
- Recovery evaluator called with stale snapshot due to race

**Risk:** Medium - Loss of network events or stale recovery decisions

---

## Missing Critical Features

### No Circuit Breaker for Failed Reconnects

**Current Behavior:** Auto-reconnects infinitely on network loss; no exponential backoff or max retry limits.

**Files:** `stream-android-core/src/main/java/io/getstream/android/core/internal/recovery/StreamConnectionRecoveryEvaluatorImpl.kt`

**Impact:**
- Server maintains dead socket connections from app crashing
- Battery drain: app repeatedly attempts connect while offline
- Network congestion during mass outage: 1M users all retrying simultaneously

**Fix Approach:**
- Implement exponential backoff with jitter for reconnect attempts
- Add max retry limit before requiring user action
- Emit `Disconnected(RECONNECT_FAILED_PERMANENTLY)` after threshold exceeded
- Document recovery behavior for SDK consumers

---

### No Request Deduplication at HTTP Layer

**Current State:** OkHttp interceptors handle individual requests; no request coalescing or caching for identical API calls.

**Impact:**
- Rapid API calls for same resource (e.g., list channels) generate multiple identical HTTP requests
- Network waste, server load spike, battery drain
- Each request blocks on token refresh if token expired

**Fix Approach:**
- Add HTTP cache (OkHttp CacheControl headers)
- Implement request coalescing: multiple identical requests awaits single response
- Add ETags/conditional request support for API endpoints

---

## Dependencies at Risk

### OkHttp Version Constraint

**Risk:** If OkHttp is pinned to old version, security patches unavailable.

**Files:** Check `stream-android-core/build.gradle.kts` for `okhttp` version constraint

**Typical Issue:** OkHttp 3.x → 4.x migration breaks interceptor API (synchronous → async patterns)

**Recommendation:**
- Pin to latest stable 4.x version (supports modern Android)
- Document required OkHttp version for consumers
- Use version range with upper bound: `"com.squareup.okhttp3:okhttp:4.+"` with max specified

---

### Moshi KSP Code Generation

**Risk:** KSP code generation may be incomplete if adapters not found at compile time.

**Files:** `stream-android-core/build.gradle.kts` (KSP plugin config)

**Impact:**
- Missing adapter → runtime `JsonAdapter` not found exception
- Silent failures if type isn't annotated with `@JsonClass(generateAdapter = true)`

**Recommendation:**
- Verify all `@JsonClass` data classes are present in main source (not in generated code)
- Add lint rule: ensure all serializable types have annotation
- Test round-trip serialization for critical event types

---

## Architecture Debt

### Event Subscription Model Complexity

**Issue:** Multiple subscription managers + composite serialization creates hard-to-trace event routing.

**Files:**
- `stream-android-core/src/main/java/io/getstream/android/core/internal/serialization/StreamCompositeEventSerializationImpl.kt:98-125` (routing logic)
- `stream-android-core/src/main/java/io/getstream/android/core/internal/socket/StreamSocketSession.kt:256-262` (listener dispatch)

**Impact:**
- Event reaches wrong listener type (core vs product)
- Difficult to debug: trace event through composite serialization → peekType → route → deserialize
- Health check events filtered late (line 258) instead of early in deserialization

**Fix Approach:**
- Simplify to single event type with tagged union (sealed class)
- Route in serializer, not after deserialization
- Eliminate health check filtering; wrap in dummy event type

---

## Known Limitations

### No Built-in Request Retry for Network Errors

**Current:** HTTP interceptors handle token errors; generic network errors not retried.

**Impact:** Transient network failures fail fast instead of auto-retry

**Recommendation:** Product SDKs implement own retry logic using `StreamRetryPolicy` for non-token errors

---

### No Connection State Prediction

**Current:** No indication of upcoming state changes (e.g., "will reconnect in 5s")

**Impact:** UI can't show "reconnecting" state smoothly; sudden transitions confuse users

**Recommendation:** Emit intermediate states (e.g., `Disconnected.Reconnecting(delay)`) if reconnect scheduled

---

*Concerns audit: 2026-01-26*
