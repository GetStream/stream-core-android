# Coding Conventions

**Analysis Date:** 2026-01-26

## Naming Patterns

**Files:**
- Implementation files: `<Name>Impl.kt` (e.g., `StreamClientImpl.kt`, `StreamWatcherImpl.kt`)
- Test files: `<ClassUnderTest>Test.kt` (e.g., `StreamRetryProcessorImplTest.kt`, `StreamWatcherImplTest.kt`)
- Model/data classes: `Stream<Entity>.kt` (e.g., `StreamConnectionState.kt`, `StreamConnectedUser.kt`)
- Interface/contract files: `Stream<Contract>.kt` (e.g., `StreamWatcher.kt`, `StreamLogger.kt`)

**Functions:**
- `camelCase` for all functions and methods
- Function names must be at least 2 characters, at most 30 characters
- Suspend functions follow standard camelCase: `suspend fun connect(...)`

**Variables:**
- `camelCase` for local variables and parameters
- Private variables: `_privateVar` or `privateVar` (with underscore optional)
- Boolean properties: Must start with `is`, `has`, or `are` (e.g., `isActive`, `hasError`)

**Types:**
- `PascalCase` for classes, interfaces, data classes, enums
- Example: `StreamRetryPolicy`, `StreamConnectionState`, `StreamWatcher<T>`

**Packages:**
- Lowercase with dots: `io.getstream.android.core.<feature>`
- Must start with `io.getstream` per detekt rule

## Code Style

**Formatting:**
- Tool: Detekt with formatting rules enabled (`autoCorrect: true`)
- Run: `./gradlew spotlessApply` (uses ktfmt internally)
- Max line length: **140 characters** (configured in detekt.yml)
- Indentation: 4 spaces (checked via `NoTabs` rule)

**Linting:**
- Tool: Detekt (static analysis)
- Run: `./gradlew detekt` (auto-corrects issues)
- Configuration: `config/detekt/detekt.yml`
- Warnings treated as errors: `warningsAsErrors: true` (detekt level 4)
- Android lint: `abortOnError: true`, `warningsAsErrors: true` (in `lint.xml`)

**Key Detekt Rules:**
- `BracesOnIfStatements`: Always require braces, single-line and multi-line
- `BracesOnWhenStatements`: Never on single-line, always on multi-line
- `DataClassShouldBeImmutable`: Data classes must be immutable
- `MaxLineLength`: 140 character limit (code lines, not imports/comments)
- `FunctionMaxLength`: Function names limited to 30 characters
- `CyclomaticComplexMethod`: Threshold of 8 (ignores simple when entries)
- `LongMethod`: Threshold of 80 lines
- `LongParameterList`: 7 parameters max for functions/constructors

## Import Organization

**Order:**
1. Kotlin standard library imports (`kotlin.*`, `kotlinx.*`)
2. Java/Android imports (`java.*`, `android.*`, `androidx.*`)
3. Third-party library imports (`io.getstream.*`, `com.squareup.*`, etc.)
4. Internal project imports (relative package imports)

**Path Aliases:**
- Not used; full qualified imports required per configuration
- Detekt rule `WildcardImport` is enabled - no wildcard imports in regular code
- Exception: Test files allow wildcard for some stdlib imports in practice

**Forbidden Imports:**
- No `java.lang.SuppressWarnings` - use `@Suppress` instead
- No `java.lang.Deprecated` - use `@Deprecated` instead
- No `println()` or `print()` - use logger instead

## Error Handling

**Pattern: Result-Based Returns**
- All public APIs return `Result<T>` instead of throwing
- Location: `stream-android-core/src/main/java/io/getstream/android/core/api/utils/Result.kt`

```kotlin
// Extension for monadic chaining
fun <T, R> Result<T>.flatMap(transform: (T) -> Result<R>): Result<R> =
    fold(onSuccess = { transform(it) }, onFailure = { Result.failure(it) })

// Observe success/failure
result.onSuccess { value -> ... }
result.onFailure { error -> ... }
result.fold(onSuccess = { ... }, onFailure = { ... })
```

**CancellationException Handling**
- CancellationException **must be rethrown**, not captured in Result
- Use `runCatchingCancellable` utility for proper semantics
- Location: `stream-android-core/src/main/java/io/getstream/android/core/api/utils/Catching.kt`

```kotlin
public inline fun <T> runCatchingCancellable(block: () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (ce: CancellationException) {
        throw ce  // Rethrow immediately
    } catch (t: Throwable) {
        Result.failure(t)
    }
}
```

**Exception Types**
- Custom exceptions inherit from standard types with proper context
- Location: `stream-android-core/src/main/java/io/getstream/android/core/api/model/exceptions/`
- Always provide message and cause for exceptions per detekt rule

## Logging

**Framework:** Lambda-based `StreamLogger` interface
- Location: `stream-android-core/src/main/java/io/getstream/android/core/api/log/StreamLogger.kt`

**Patterns:**
- Use lambda parameters to defer string computation: `logger.d { "message" }`
- Level methods: `d()`, `i()`, `w()`, `e()`, `v()`
- Error logging with exception: `logger.e(throwable) { "context message" }`

```kotlin
// Good: Lambda defers computation until log level check passes
logger.v { "Expensive: ${expensiveComputation()}" }

// Bad: String always computed
logger.d("String: " + expensiveComputation())
```

**Levels:**
- **Verbose** (1): Detailed debugging, low-level flow
- **Debug** (2): General debugging information
- **Info** (3): Normal operation milestones
- **Warning** (4): Non-fatal issues, recoverable conditions
- **Error** (5): Recoverable failures, exceptions caught

**Common Patterns:**
- Connection state changes: Log at Verbose level
- Retry attempts: Log at Verbose with attempt number
- Listener errors: Log at Verbose with error context
- Component lifecycle: Log at Debug level

## Comments

**When to Comment:**
- Public APIs require KDoc (checked by detekt `UndocumentedPublicClass`, `UndocumentedPublicFunction`)
- Complex algorithms: Explain non-obvious logic
- Non-obvious parameter names: Document expectations
- Invariants: Document assumptions about state

**JSDoc/KDoc:**
- Required for all public classes and functions
- Required for public properties
- Excluded in test files (per detekt config)

```kotlin
/**
 * Manages watched resources and triggers re-watching on connection state changes.
 *
 * @param scope Coroutine scope for async operations
 * @param connectionState StateFlow providing connection state updates
 * @param watched Registry of watched entries
 * @param rewatchSubscriptions Subscription manager for rewatch listeners
 * @param logger Logger for diagnostics
 */
internal class StreamWatcherImpl<T>(
    private val scope: CoroutineScope,
    private val connectionState: StateFlow<StreamConnectionState>,
    ...
)
```

**Forbidden Comments:**
- `TODO:` - Forbidden by detekt rule `ForbiddenComment`
- `FIXME:` - Forbidden by detekt rule `ForbiddenComment`
- `STOPSHIP:` - Forbidden by detekt rule `ForbiddenComment`

## Function Design

**Size Guidelines:**
- Max 80 lines per function (detekt `LongMethod` rule)
- Prefer small focused functions for testability
- Early returns preferred for guard clauses

**Parameters:**
- Max 7 parameters for functions and constructors (detekt rule)
- Use named arguments when calling functions with 3+ parameters
- Constructor parameters may be annotated with detekt rule `ConstructorParameterNaming`

**Return Values:**
- Single return statement preferred (detekt `ReturnCount` max: 1)
- Exception: Excludes `equals` method and guard clauses
- Return from lambda: OK (excludeReturnFromLambda: true)

## Module Design

**Exports:**
- Internal APIs marked with `@StreamInternalApi` annotation
- Published APIs marked with `@StreamPublishedApi` annotation
- Delicate APIs marked with `@StreamDelicateApi` annotation
- All public declarations must have visibility modifier (explicit API enabled)

**Barrel Files:**
- Factory functions in interface files (e.g., `StreamWatcher.kt` contains the factory)
- Implementation suffix pattern: `StreamWatcher` interface with `StreamWatcherImpl` implementation

**Visibility:**
- `public` for published APIs (SDK-exposed)
- `internal` for internal infrastructure
- `private` for private helpers within class/file

## Special Patterns

**Data Classes:**
- Must use `@JsonClass(generateAdapter = true)` for Moshi serialization
- Moshi adapters generated via KSP at compile time
- Keep immutable (all properties `val`)
- Location: `stream-android-core/src/main/java/io/getstream/android/core/api/model/`

```kotlin
@JsonClass(generateAdapter = true)
data class MyEvent(
    val type: String,
    val data: String,
    val timestamp: Date
)
```

**Sealed Classes for Polymorphism:**
- Used for state machines and union types
- Example: `StreamConnectionState` (Idle, Connecting, Connected, Disconnected)
- Detekt enforces exhaustive when expressions: `checkExhaustiveness: true`

**Extension Functions:**
- Placed in dedicated utility files matching their receiver type
- Convention: `<Receiver>Extensions.kt` or `<Feature>.kt`
- Examples: `Flows.kt`, `Result.kt`, `Catching.kt`

**Atomic Operations:**
- Use `AtomicBoolean`, `AtomicInteger`, `AtomicReference` for thread-safe flags
- Example: `AtomicBoolean(false)` with `compareAndSet()` for idempotency guards

---

*Convention analysis: 2026-01-26*
