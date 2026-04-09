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
import io.getstream.android.core.api.log.StreamLogger
import io.getstream.android.core.internal.processing.StreamThrottlerImpl
import kotlinx.coroutines.CoroutineScope

/**
 * Rate-limits a bursty stream of values so that at most **one** value is delivered per time window.
 *
 * The first value submitted is delivered immediately (leading edge). Subsequent values arriving
 * within the [windowMs] window are dropped. After the window expires, the next submission is
 * delivered immediately, starting a new window.
 *
 * ### Semantics
 * - **Leading-edge:** The first value in each window is delivered immediately.
 * - **Drop excess:** Values arriving during an active window are silently dropped.
 * - **No trailing delivery:** Unlike [StreamDebouncer], there is no delayed trailing emission.
 * - **Thread-safety:** All functions are safe to call from multiple coroutines.
 *
 * ### Usage
 * - Typing indicators: Emit at most once per 3 seconds.
 * - Read receipts: Don't flood the server with mark-read calls.
 * - Notification delivery: Rate-limit push notification display.
 *
 * ```kotlin
 * val throttler = StreamThrottler<TypingEvent>(scope, logger, windowMs = 3_000)
 * throttler.onValue { event -> api.sendTypingEvent(event) }
 *
 * // Rapid calls — only the first gets through per 3s window
 * throttler.submit(event1) // delivered immediately
 * throttler.submit(event2) // dropped (within window)
 * throttler.submit(event3) // dropped (within window)
 * // ... 3 seconds pass ...
 * throttler.submit(event4) // delivered immediately (new window)
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
     * Submits a value. If no window is active, the value is delivered immediately and a new window
     * starts. If a window is active, the value is dropped.
     *
     * @param value The value to throttle.
     * @return `true` if the value was accepted (delivered), `false` if it was dropped.
     */
    public fun submit(value: T): Boolean

    /** Resets the throttle window, allowing the next [submit] to pass through immediately. */
    public fun reset()
}

/**
 * Creates a new [StreamThrottler] instance.
 *
 * @param T The type of value being throttled.
 * @param scope Coroutine scope for launching the delivery.
 * @param logger Logger for diagnostics.
 * @param windowMs Minimum time in milliseconds between accepted values. Defaults to 3000ms.
 * @return A new [StreamThrottler] instance.
 */
@StreamInternalApi
public fun <T> StreamThrottler(
    scope: CoroutineScope,
    logger: StreamLogger,
    windowMs: Long = 3_000L,
): StreamThrottler<T> = StreamThrottlerImpl(scope = scope, logger = logger, windowMs = windowMs)
