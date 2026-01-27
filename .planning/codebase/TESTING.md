# Testing Patterns

**Analysis Date:** 2026-01-26

## Test Framework

**Runner:**
- JUnit 4 (dependency: `junit` via gradle libs)
- Kotlin test support (dependency: `kotlin("test")`)
- Config: Build configuration in `stream-android-core/build.gradle.kts`

**Assertion Library:**
- `org.junit.Assert.*` for primary assertions
- `kotlin.test.assert*` functions available as alternatives
- Assertions: `assertTrue()`, `assertFalse()`, `assertEquals()`, `assertSame()`, `assertNull()`, `assertThrows()`

**Run Commands:**
```bash
# Run all unit tests
./gradlew :stream-android-core:test

# Run specific test class
./gradlew :stream-android-core:test --tests "io.getstream.android.core.internal.processing.StreamRetryProcessorImplTest"

# Run specific test method
./gradlew :stream-android-core:test --tests "io.getstream.android.core.internal.processing.StreamRetryProcessorImplTest.testExponentialBackoff"

# Run tests with coverage report
./gradlew koverHtmlReportCoverage
# Coverage report: stream-android-core/build/reports/kover/htmlCoverage/index.html

# Run instrumented tests on connected device
./gradlew :stream-android-core:connectedDebugAndroidTest
```

## Test File Organization

**Location:**
- Unit tests: `stream-android-core/src/test/java/io/getstream/android/core/`
- Instrumented tests: `stream-android-core/src/androidTest/java/io/getstream/android/core/`
- Mirror source package structure: Test class location mirrors source class location

**Naming:**
- Pattern: `<ClassUnderTest>Test.kt`
- Examples:
  - `stream-android-core/src/test/java/io/getstream/android/core/internal/processing/StreamRetryProcessorImplTest.kt`
  - `stream-android-core/src/test/java/io/getstream/android/core/internal/watcher/StreamWatcherImplTest.kt`
  - `stream-android-core/src/test/java/io/getstream/android/core/internal/processing/StreamSingleFlightProcessorImplTest.kt`

**Structure:**
```
stream-android-core/src/test/java/io/getstream/android/core/
├── internal/
│   ├── processing/
│   │   ├── StreamRetryProcessorImplTest.kt
│   │   ├── StreamBatcherImplTest.kt
│   │   ├── StreamSingleFlightProcessorImplTest.kt
│   │   └── StreamSerialProcessingQueueImplTest.kt
│   ├── watcher/
│   │   └── StreamWatcherImplTest.kt
│   ├── serialization/
│   │   ├── StreamMoshiJsonSerializationImplTest.kt
│   │   └── StreamCompositeEventSerializationImplTest.kt
│   └── ...
```

## Test Structure

**Suite Organization:**

Test classes use JUnit 4 structure with `@Before` setup and organized `@Test` methods:

```kotlin
class StreamRetryProcessorImplTest {

    private val retry = StreamRetryProcessorImpl(mockk(relaxed = true))

    @Test
    fun `returns success immediately when block succeeds on first attempt`() = runTest {
        val policy = StreamRetryPolicy.linear(initialDelayMillis = 0)
        val counter = AtomicInteger()

        val res = retry.retry(policy) {
            counter.incrementAndGet()
            "OK"
        }

        assertTrue(res.isSuccess)
        assertEquals("OK", res.getOrNull())
        assertEquals(1, counter.get())
    }
}
```

**Patterns:**

**Setup Pattern:**
```kotlin
class StreamWatcherImplTest {

    private lateinit var logger: StreamLogger
    private lateinit var rewatchSubscriptions: StreamSubscriptionManager<StreamRewatchListener<String>>
    private lateinit var connectionState: MutableStateFlow<StreamConnectionState>
    private lateinit var watcher: StreamWatcher<String>
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        logger = mockk(relaxed = true)
        rewatchSubscriptions = mockk(relaxed = true)
        connectionState = MutableStateFlow(StreamConnectionState.Idle)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        watcher = StreamWatcherImpl(
            scope = scope,
            connectionState = connectionState,
            rewatchSubscriptions = rewatchSubscriptions,
            logger = logger,
        )
    }
}
```

**Teardown Pattern:**
- Implicit via test framework lifecycle
- Coroutine scopes automatically cleaned up after test
- No explicit `@After` needed for mocks (MockK handles cleanup)

**Assertion Pattern:**
```kotlin
@Test
fun `watch adds CID to registry`() {
    val cid = "messaging:general"

    val result = watcher.watch(cid)

    assertTrue(result.isSuccess)
    assertEquals(cid, result.getOrNull())
    assertTrue(watched.containsKey(cid))
}
```

**Test Naming Convention:**
- Backtick names describing behavior: `` `function does something when condition` ``
- Examples:
  - `` `returns success immediately when block succeeds on first attempt` ``
  - `` `watch adds CID to registry` ``
  - `` `retries until block succeeds before reaching maxRetries` ``

## Mocking

**Framework:** MockK
- Dependency: `mockk` via gradle libs
- Supports both blocking and suspend functions via `coEvery`, `coVerify`

**Patterns:**

**Basic Mocking:**
```kotlin
val logger: StreamLogger = mockk(relaxed = true)  // Relaxed: all calls return default values
val worker: Worker = mockk()  // Strict: must specify behavior

coEvery { worker.workInt() } coAnswers {
    delay(1_000)
    42
}
```

**Verification:**
```kotlin
coVerify(exactly = 1) { worker.workInt() }  // Verify called exactly once
coVerify(atLeast = 2) { worker.workInt() }  // Verify called at least twice
verify(exactly = 0) { worker.workInt() }    // Verify never called
```

**Custom Test Collaborators:**
```kotlin
class RecordingMap<K, V>(private val delegate: ConcurrentMap<K, V> = ConcurrentHashMap()) :
    ConcurrentMap<K, V> by delegate {
    val installedNonNull = java.util.concurrent.atomic.AtomicBoolean(false)

    override fun putIfAbsent(key: K, value: V): V? {
        val existing = delegate.putIfAbsent(key, value)
        if (existing != null) installedNonNull.set(true)
        return existing
    }
}
```

**What to Mock:**
- Logger instances (always relaxed: `mockk(relaxed = true)`)
- Listener interfaces/callbacks for event verification
- Collaborators with side effects or long-running operations
- Dependencies that would require real network/database

**What NOT to Mock:**
- Value objects and data classes - instantiate directly
- Sealed classes and enums - use actual instances
- Result types - use `Result.success()` and `Result.failure()`
- StateFlow/Flow - use `MutableStateFlow` instead
- Coroutine-related utilities from kotlinx.coroutines - use real implementations

## Fixtures and Factories

**Test Data:**
- Test uses inline factory functions within test files
- No separate fixture library; data created directly in tests
- Use `mockk` for complex object creation with specific behaviors

**Example Fixture:**
```kotlin
@Test
fun `watch adds CID to registry`() {
    val cid = "messaging:general"  // Inline test data

    val result = watcher.watch(cid)

    assertTrue(result.isSuccess)
}
```

**Location:**
- Fixtures created locally within test methods or `@Before` setup
- Shared test data in top-level test class properties
- No separate test fixtures directory

## Coverage

**Requirements:**
- No enforced minimum coverage percentage
- Kover integrated for measurement and HTML reporting
- Coverage analyzed per module

**View Coverage:**
```bash
# Generate coverage report
./gradlew koverHtmlReportCoverage

# Open HTML report
open stream-android-core/build/reports/kover/htmlCoverage/index.html
```

**Kover Configuration:**
- Tool: Kover (Kotlin coverage engine)
- Report format: HTML with line/instruction/branch coverage
- Integration: Gradle plugin configured in build.gradle.kts

## Test Types

**Unit Tests:**
- Scope: Individual functions and classes in isolation
- Approach: Mock external dependencies, test logic paths
- Location: `stream-android-core/src/test/java/`
- Examples:
  - `StreamRetryProcessorImplTest.kt` - Tests retry logic with mocked logger
  - `StreamSingleFlightProcessorImplTest.kt` - Tests deduplication with custom mocks
  - `StreamWatcherImplTest.kt` - Tests watch registry and state observation

**Integration Tests:**
- Scope: Multiple components working together (without network)
- Approach: Real implementations, no mocking except external services
- Example: Connection state change triggering rewatch callbacks
- Use: `MutableStateFlow` for state injection, real coroutine scopes

**E2E Tests:**
- Status: Not implemented (Android Instrumented tests possible via `connectedDebugAndroidTest`)
- Would require device/emulator setup
- Not currently in scope for unit test suite

## Common Patterns

**Async Testing:**
```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
@Test
fun `retries until block succeeds before reaching maxRetries`() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = TestScope(dispatcher)

    val counter = AtomicInteger(1)
    val job = scope.async {
        retry.retry(policy) {
            when (counter.incrementAndGet()) {
                1, 2 -> error("Boom")
                else -> "OK"
            }
        }
    }

    dispatcher.scheduler.advanceUntilIdle()  // Execute scheduled delays

    val res = job.await()
    assertTrue(res.isSuccess)
}
```

**Coroutine Testing with TestDispatchers:**
- Use `runTest { ... }` from `kotlinx.coroutines.test`
- Create `StandardTestDispatcher` for virtual time control
- Advance time: `advanceUntilIdle()` or `advanceTimeBy(ms)`
- Verify suspension behavior without real delays

**Error Testing:**
```kotlin
@Test
fun `returns failure after exhausting maxRetries`() = runTest {
    val policy = StreamRetryPolicy.linear(maxRetries = 2, minRetries = 1)
    val boom = RuntimeException("boom")

    val res = retry.retry(policy) { throw boom }

    assertTrue(res.isFailure)
    assertSame(boom, res.exceptionOrNull())  // Exact same exception object
}
```

**State Flow Observation Testing:**
```kotlin
@Test
fun `triggers rewatch on connection state change`() = runTest {
    val connectionState = MutableStateFlow<StreamConnectionState>(StreamConnectionState.Idle)

    watcher.start()
    watcher.watch("channel:1")

    // Emit Connected state
    connectionState.value = StreamConnectionState.Connected(
        user = mockk(),
        connectionId = "conn-123"
    )

    advanceUntilIdle()  // Let collector process state change

    // Verify listener was invoked with correct data
    verify { listener.onRewatch(setOf("channel:1"), "conn-123") }
}
```

**Robolectric for Android Components:**
```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class LifecycleMonitorTest {

    @Test
    fun testLifecycleMonitor() {
        // Can use real Android components like ProcessLifecycleOwner
        val monitor = StreamLifecycleMonitorImpl(logger)
        // Test with actual Android lifecycle callbacks
    }
}
```

## Test Isolation & Cleanup

**Resource Management:**
- Coroutine scopes created with `SupervisorJob()` in setup
- Dispatcher resets between test methods
- MockK automatically clears mocks after each test
- MutableStateFlow instances created fresh per test

**Concurrency Testing:**
```kotlin
@Test
fun `concurrent start and stop calls are thread-safe`() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(SupervisorJob() + dispatcher)

    val jobs = (1..10).map {
        scope.async { watcher.start() }
    } + (1..10).map {
        scope.async { watcher.stop() }
    }

    dispatcher.scheduler.advanceUntilIdle()

    jobs.forEach { job -> job.await() }
    // Verify final state is consistent
}
```

---

*Testing analysis: 2026-01-26*
