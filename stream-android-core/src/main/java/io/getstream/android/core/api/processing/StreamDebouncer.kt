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
import io.getstream.android.core.internal.processing.StreamDebouncerImpl
import kotlinx.coroutines.CoroutineScope

/**
 * Debounces a bursty stream of values so that only the **last** value is delivered after a quiet
 * period.
 *
 * Each call to [submit] replaces the pending value and resets the delay timer. When the timer
 * expires without a new submission, the registered [onValue] handler is invoked with the most
 * recent value. This is useful for coalescing rapid state changes (network flaps, lifecycle
 * transitions) into a single settled action.
 *
 * ### Semantics
 * - **Last-write-wins:** Only the most recently submitted value is delivered.
 * - **Timer reset:** Each [submit] cancels any pending delivery and restarts the delay.
 * - **Thread-safety:** All functions are safe to call from multiple coroutines.
 * - **Cancellation:** [cancel] discards the pending value and stops the timer without delivery.
 */
@StreamInternalApi
public interface StreamDebouncer<T> {
    /**
     * Registers the handler invoked when the debounce window elapses.
     *
     * @param callback Suspend function called with the settled value.
     */
    public fun onValue(callback: suspend (T) -> Unit)

    /**
     * Submits a new value, cancelling any pending delivery and restarting the delay timer.
     *
     * @param value The value to deliver after the debounce window.
     */
    public fun submit(value: T)

    /** Cancels any pending delivery without invoking the handler. */
    public fun cancel()
}

/**
 * Creates a new [StreamDebouncer] instance.
 *
 * @param T The type of value being debounced.
 * @param scope Coroutine scope for launching the delayed delivery job.
 * @param logger Logger for diagnostics.
 * @param delayMs Time in milliseconds to wait after the last [StreamDebouncer.submit] before
 *   delivering the value. Defaults to 300ms.
 * @return A new [StreamDebouncer] instance.
 */
@StreamInternalApi
public fun <T> StreamDebouncer(
    scope: CoroutineScope,
    logger: StreamLogger,
    delayMs: Long = 300L,
): StreamDebouncer<T> = StreamDebouncerImpl(scope = scope, logger = logger, delayMs = delayMs)
