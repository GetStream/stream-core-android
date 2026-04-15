/*
 * Copyright (c) 2014-2026 Stream.io Inc. All rights reserved.
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
import io.getstream.android.core.api.log.StreamLogger
import io.getstream.android.core.internal.processing.StreamThrottlerImpl
import kotlinx.coroutines.CoroutineScope

/**
 * Rate-limits a bursty stream of values so that at most **one** value is delivered per time window.
 *
 * Behaviour depends on the [StreamThrottlePolicy]:
 * - **[Leading][StreamThrottlePolicy.Leading]:** First value delivered immediately, rest dropped
 *   until window expires. Best for: typing indicators, presence updates.
 * - **[Trailing][StreamThrottlePolicy.Trailing]:** Values collected during the window, only the
 *   **last** value delivered when the window expires. Best for: position/progress updates where
 *   latest state matters.
 * - **[LeadingAndTrailing][StreamThrottlePolicy.LeadingAndTrailing]:** First value delivered
 *   immediately, AND the last value delivered when the window expires (if a newer value was
 *   submitted during the window). Best for: scroll position tracking, where both responsiveness and
 *   final accuracy matter.
 *
 * ### Semantics
 * - **Thread-safety:** All functions are safe to call from multiple coroutines.
 * - **Cancellation:** [reset] clears any pending trailing delivery and resets the window.
 *
 * ### Usage
 *
 * ```kotlin
 * // Typing indicators — leading edge, fire immediately, suppress duplicates
 * val throttler = StreamThrottler<TypingEvent>(scope, logger, policy = StreamThrottlePolicy.leading())
 * throttler.onValue { event -> api.sendTypingEvent(event) }
 *
 * // Position updates — trailing edge, only latest state matters
 * val throttler = StreamThrottler<Position>(scope, logger, policy = StreamThrottlePolicy.trailing())
 *
 * // Scroll tracking — both edges
 * val throttler = StreamThrottler<Int>(scope, logger, policy = StreamThrottlePolicy.leadingAndTrailing())
 * ```
 */
@StreamInternalApi
public interface StreamThrottler<T> {
    /**
     * Registers the handler invoked when a value passes through the throttle.
     *
     * @param callback Suspend function called with the accepted value.
     */
    public fun onValue(callback: suspend (T) -> Unit)

    /**
     * Submits a value. Behaviour depends on the configured [StreamThrottlePolicy].
     *
     * @param value The value to throttle.
     * @return `true` if the value was accepted for delivery (immediate or pending), `false` if it
     *   was dropped entirely.
     */
    public fun submit(value: T): Boolean

    /** Resets the throttle window and discards any pending trailing delivery. */
    public fun reset()
}

/**
 * Creates a new [StreamThrottler] instance.
 *
 * @param T The type of value being throttled.
 * @param scope Coroutine scope for launching the delivery.
 * @param logger Logger for diagnostics.
 * @param policy The throttle strategy. Defaults to [StreamThrottlePolicy.leading] with 3000ms
 *   window.
 * @return A new [StreamThrottler] instance.
 */
@StreamInternalApi
public fun <T> StreamThrottler(
    scope: CoroutineScope,
    logger: StreamLogger,
    policy: StreamThrottlePolicy = StreamThrottlePolicy.leading(),
): StreamThrottler<T> = StreamThrottlerImpl(scope = scope, logger = logger, policy = policy)
