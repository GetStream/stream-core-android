# Technology Stack

**Analysis Date:** 2026-01-26

## Languages

**Primary:**
- Kotlin 2.2.0 - Primary development language for entire library
- Java 11 (JVM Target) - Compilation target

**Secondary:**
- XML - Android configuration files (lint.xml, AndroidManifest.xml)
- ProGuard - Obfuscation rules (proguard-rules.pro)

## Runtime

**Environment:**
- Android minSdk 21 (Android 5.0)
- Android compileSdk 36 (Android 15)
- Android targetSdk 36 (Android 15)

**Package Manager:**
- Gradle 8.11.1 (AGP - Android Gradle Plugin)
- Lockfile: gradle-wrapper.properties (gradle wrapper configured)

## Frameworks

**Core:**
- Kotlin Coroutines 1.10.2 - Async/await, StateFlow, structured concurrency

**Android:**
- AndroidX Core 1.7.0 - Core Android support library
- AndroidX Lifecycle 2.9.4 - Lifecycle observers and management
- AndroidX Lifecycle Process 2.9.4 - ProcessLifecycleOwner for app background/foreground detection

**Networking:**
- OkHttp 5.1.0 - HTTP client with WebSocket and interceptor support
- OkHttp Logging 5.1.0 - Request/response logging interceptor
- Retrofit 3.0.0 - REST API client (primarily for SDK consumer usage)
- Moshi 1.15.2 - JSON serialization/deserialization

**Build/Dev:**
- Detekt 1.23.8 - Kotlin static analysis (code quality)
- KSP 2.2.0-2.0.2 - Kotlin Symbol Processing for code generation (Moshi adapters)
- Lint API 31.12.0 - Android lint for custom lint rules

**Testing:**
- JUnit 4.13.2 - Unit test framework
- Kotlin Test Library - Kotlin test extensions
- Robolectric 4.15.1 - Android component testing on JVM
- MockK 1.14.5 - Kotlin mocking framework
- OkHttp MockWebServer 5.1.0 - WebSocket and HTTP mocking
- Kotlinx Coroutines Test 1.10.2 - Coroutine testing utilities

## Key Dependencies

**Critical (Core Functionality):**
- okhttp (5.1.0) - WebSocket communication with Stream backend
- moshi (1.15.2) - JSON serialization for API requests/responses
- kotlinx-coroutines (1.10.2) - Async operations and state management
- androidx-lifecycle (2.9.4) - Lifecycle-aware connection state and monitoring

**Infrastructure:**
- retrofit (3.0.0) - Optional REST API support for SDK consumers
- androidx-annotation-jvm (1.9.1) - Annotations for Lint checks
- stream-conventions (0.5.0) - Custom build configuration plugin

**Testing Only:**
- robolectric (4.15.1) - Simulates Android framework in unit tests
- mockk (1.14.5) - Mocking Kotlin objects
- mockwebserver (5.1.0) - Mock WebSocket/HTTP responses

## Configuration

**Environment:**
- Properties-based: `gradle.properties` contains project version (2.0.0) and build settings
- Local overrides: `local.properties` for machine-specific settings
- No .env files or external configuration loading

**Key gradle.properties Settings:**
```
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
version=2.0.0
```

**Build Configuration:**
- Multi-module Gradle project (settings.gradle.kts defines modules)
- Kotlin explicit API mode enabled - all public APIs must have visibility modifiers
- KSP configured for Moshi code generation (line 72 in stream-android-core/build.gradle.kts)
- Detekt auto-correct enabled for code quality fixes

**Publishing:**
- Maven Central publishing (stream-conventions plugin handles versioning)
- Consumer ProGuard rules: `consumer-rules.pro` distributed with library

## Platform Requirements

**Development:**
- Android Studio 2024.x or later (API 36 support)
- Gradle 8.11.1 (via gradle wrapper)
- JDK 11+ (explicit JVM target 11)
- Kotlin 2.2.0 compiler
- KSP compatible IDE/tools

**Production (Consumer Apps):**
- Android API 21+ (Android 5.0 Lollipop)
- OkHttp 5.x available (for WebSocket HTTP upgrade)
- Coroutines support (standard for modern Android)
- ProcessLifecycleOwner availability (AndroidX Lifecycle)

**Network Requirements:**
- WebSocket-capable HTTP server
- Stream backend URL (wss://...)
- JWT token endpoint for authentication

## Versioning & Release

**Library Version:** 2.0.0 (defined in gradle.properties)

**Kotlin Compatibility:**
- Kotlin 2.2.0 (latest at analysis date)
- Targets JVM 11 bytecode

**AndroidX Compatibility:**
- Uses non-transitive R classes (android.nonTransitiveRClass=true)
- Explicit AndroidX usage (android.useAndroidX=true)

## Code Generation & Processing

**KSP (Kotlin Symbol Processing):**
- Location: `stream-android-core/build.gradle.kts:72`
- Plugin: `ksp` version 2.2.0-2.0.2
- Used for: Moshi JSON adapter code generation
- Models: Located in `stream-android-core/src/main/java/io/getstream/android/core/api/model/`
- Annotations: `@JsonClass(generateAdapter = true)` on data classes

**Detekt (Static Analysis):**
- Config: `config/detekt/detekt.yml` (root directory)
- Auto-correct enabled for formatting issues
- Formatting plugin: `detekt-formatting` 1.23.8
- Used for: Code quality checks (lines 35-40 in build.gradle.kts)

## Custom Lint Rules

**Location:** `stream-android-core-lint/` module

**Rules Enforced:**
- StreamApiExplicitMarker - Ensures all public APIs have visibility annotations
- SuspendRunCatching - Prevents improper exception handling in suspend functions
- ExposeAsStateFlow - Prevents exposing mutable state flows
- NoPublicInInternalPackages - Enforces encapsulation of internal packages
- NotKeepingInstance - Ensures StreamClient instances are retained

---

*Stack analysis: 2026-01-26*
