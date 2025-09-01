/*
 * Copyright (c) 2014-2025 Stream.io Inc. All rights reserved.
 *
 * Licensed under the Stream License;
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://github.com/GetStream/stream-core-android/blob/main/LICENSE
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.getstream.android.core.annotations

/**
 * DSL scope marker for Stream SDK builder blocks.
 *
 * Use this annotation to limit the scope of receivers in nested builder DSLs. It prevents
 * accidental calls to the wrong receiver when multiple DSL contexts are in play.
 */
@DslMarker annotation class StreamDsl

/**
 * Marks an **internal Stream SDK API** that is not intended for public use.
 *
 * Opting in means you acknowledge that:
 * - The API may **change or be removed at any time** without prior notice.
 * - **Support may be limited or unavailable** for issues caused by using this API.
 *
 * ### How to enable
 *
 * ```kotlin
 * @OptIn(StreamInternalApi::class)
 * ```
 */
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.TYPEALIAS,
)
@Retention(AnnotationRetention.BINARY)
@RequiresOptIn(
    message =
        "Internal Stream SDK API – may change or be removed at any time. " +
            "By opting in you accept that you might not receive official support " +
            "for issues caused by using this function or type.",
    level = RequiresOptIn.Level.ERROR,
)
annotation class StreamInternalApi

/**
 * Marks a **delicate or advanced Stream SDK API** that should be used with caution.
 *
 * Opting in means you understand that:
 * - The API may **change or be removed without notice**.
 * - **Support may be limited or unavailable** for issues caused by using this API.
 * - You are fully aware of the potential risks and consequences of using it.
 *
 * You can optionally provide a [message] with additional context that will be displayed as part of
 * the compiler warning when this API is used.
 *
 * ### How to enable
 *
 * ```kotlin
 * @OptIn(StreamDelicateApi::class)
 * ```
 */
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.TYPEALIAS,
)
@Retention(AnnotationRetention.BINARY)
@RequiresOptIn(
    message =
        "Delicate Stream SDK API – use only when you fully understand the " +
            "implications. By opting in you acknowledge that this call path may " +
            "receive limited or no official support and can change without notice.",
    level = RequiresOptIn.Level.WARNING,
)
annotation class StreamDelicateApi(val message: String)

/**
 * Marks APIs that are part of the **Stream core SDK layer**.
 * This API can be safely published and used by other Stream SDKs. They can also be propagated and exposed via
 * public APIs of the product SDKs.
 */
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.TYPEALIAS,
)
@Retention(AnnotationRetention.BINARY)
annotation class StreamPublishedApi
