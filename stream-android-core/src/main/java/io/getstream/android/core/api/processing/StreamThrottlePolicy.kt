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

package io.getstream.android.core.api.processing

import io.getstream.android.core.annotations.StreamInternalApi

/**
 * Throttle strategy that controls **when** values are delivered within a time window.
 *
 * ### Strategies
 *
 * **[Leading]** — First value in the window is delivered immediately. Subsequent values are dropped
 * until the window expires.
 *
 * ```
 * submit: A---B-C-D-----------E-F---
 * output: A--------------------E-----
 *          |--- windowMs ---|   |--- windowMs ---|
 * ```
 *
 * **[Trailing]** — Values are collected during the window. Only the **last** value is delivered
 * when the window expires.
 *
 * ```
 * submit: A---B-C-D-----------E-F---
 * output: -----------D--------------F
 *          |--- windowMs ---|   |--- windowMs ---|
 * ```
 *
 * **[LeadingAndTrailing]** — First value delivered immediately. The last value in the window is
 * also delivered when the window expires (if different from the leading value).
 *
 * ```
 * submit: A---B-C-D-----------E-F---
 * output: A----------D--------E----F
 *          |--- windowMs ---|   |--- windowMs ---|
 * ```
 */
@StreamInternalApi
public sealed interface StreamThrottlePolicy {

    /** Minimum time in milliseconds between accepted values. */
    public val windowMs: Long

    /**
     * First value passes immediately; subsequent values within the window are dropped.
     *
     * @property windowMs Minimum time between delivered values. Defaults to 3000ms.
     */
    public data class Leading
    internal constructor(override val windowMs: Long = DEFAULT_WINDOW_MS) : StreamThrottlePolicy

    /**
     * Values collected during the window; only the last value is delivered when the window expires.
     *
     * @property windowMs Collection window duration. Defaults to 3000ms.
     */
    public data class Trailing
    internal constructor(override val windowMs: Long = DEFAULT_WINDOW_MS) : StreamThrottlePolicy

    /**
     * First value passes immediately; the last value is also delivered when the window expires (if
     * a newer value was submitted during the window).
     *
     * @property windowMs Minimum time between delivered values. Defaults to 3000ms.
     */
    public data class LeadingAndTrailing
    internal constructor(override val windowMs: Long = DEFAULT_WINDOW_MS) : StreamThrottlePolicy

    /** Factory methods for creating [StreamThrottlePolicy] instances. */
    public companion object {

        /** Default throttle window: 3 seconds. */
        public const val DEFAULT_WINDOW_MS: Long = 3_000L

        /** Creates a [Leading] policy with the given [windowMs]. */
        public fun leading(windowMs: Long = DEFAULT_WINDOW_MS): StreamThrottlePolicy =
            Leading(windowMs).validate()

        /** Creates a [Trailing] policy with the given [windowMs]. */
        public fun trailing(windowMs: Long = DEFAULT_WINDOW_MS): StreamThrottlePolicy =
            Trailing(windowMs).validate()

        /** Creates a [LeadingAndTrailing] policy with the given [windowMs]. */
        public fun leadingAndTrailing(windowMs: Long = DEFAULT_WINDOW_MS): StreamThrottlePolicy =
            LeadingAndTrailing(windowMs).validate()

        private fun <T : StreamThrottlePolicy> T.validate(): T {
            require(windowMs > 0) { "windowMs must be > 0, was $windowMs" }
            return this
        }
    }
}
