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

import io.getstream.android.core.annotations.StreamCoreApi
import io.getstream.android.core.internal.processing.StreamBatcherImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel

/**
 * Batches a bursty stream of items into **batches** delivered at controlled intervals.
 *
 * Implementations buffer items submitted via [enqueue] and periodically invoke the registered
 * [onBatch] handler with the items collected so far (in arrival order). A batch is typically
 * emitted when either:
 * - a size threshold is reached, or
 * - a debounce window elapses.
 *
 * Exact batching policy (batch size, initial window, backoff, etc.) is implementation-defined.
 *
 * ### Semantics
 * - **Batch delivery:** Items are passed to [onBatch] as an immutable snapshot. The same item is
 *   never delivered twice.
 * - **Ordering:** Items in a batch are ordered by arrival time; batches preserve overall order.
 * - **Start/stop:** Processors usually start lazily on the first [enqueue] and stop with [stop].
 * - **Backpressure:** [enqueue] is `suspend` and may block if the internal buffer is bounded and
 *   full.
 * - **Thread-safety:** All functions are safe to call from multiple coroutines.
 *
 * ### Typical use
 * - Coalescing high-frequency events (socket messages, UI updates) into fewer handler invocations.
 * - Rate-limiting downstream work (parsing, I/O, UI recomposition).
 */
@StreamCoreApi
interface StreamBatcher<T> {
    /**
     * Starts the processor if it's not already running.
     *
     * @return `Result.success(Unit)` if the processor was started successfully; otherwise a
     *   `Result.failure(cause)` describing why the start failed.
     */
    suspend fun start(): Result<Unit>

    /**
     * Registers the batch handler to be invoked whenever a batch is ready.
     *
     * The handler is called on the processor's internal coroutine context.
     *
     * @param handler A suspending function receiving:
     *     - **List<T>**: the batch snapshot in arrival order,
     *     - **Long**: the debounce window applied (milliseconds) for this emission (telemetry;
     *       value meaning is implementation-defined),
     *     - **Int**: the number of items emitted in this batch (equals `batch.size`).
     *
     * Calling this method replaces any previously registered handler.
     */
    fun onBatch(handler: suspend (List<T>, Long, Int) -> Unit)

    /**
     * Enqueues a single item for debounced processing.
     *
     * This function **suspends** if the underlying buffer is full (bounded-capacity
     * implementations), providing natural backpressure. It returns a [Result]:
     * - `Result.success(Unit)` if the item was accepted,
     * - `Result.failure(cause)` if the processor is closed/stopped or cannot accept the item.
     *
     * Implementations may start processing lazily on the first call.
     *
     * @param item The item to enqueue.
     */
    suspend fun enqueue(item: T): Result<Unit>

    /**
     * Enqueues a single item for debounced processing.
     *
     * This function **does not suspend** and returns `false` if the underlying buffer is full
     * (bounded-capacity implementations). It returns a [Result]:
     * - `true` if the item was accepted,
     * - `false` if the processor is closed/stopped or cannot accept the item.
     */
    fun offer(item: T): Boolean

    /**
     * Stops the processor and releases resources.
     *
     * After calling this, no further batches will be emitted and subsequent [enqueue] or [offer]
     * calls should fail with `Result.failure`. Multiple calls to [stop] are allowed and should be
     * **idempotent**.
     *
     * @return `Result.success(Unit)` on a successful stop; `Result.failure(cause)` if stopping
     *   failed.
     */
    fun stop(): Result<Unit>
}

/**
 * Creates a new [StreamBatcher] instance.
 *
 * @param T The type of items to be processed.
 * @param scope The coroutine scope.
 * @param batchSize The maximum number of items to be processed in a batch.
 * @param initialDelayMs The initial delay in milliseconds before processing a batch.
 * @param maxDelayMs The maximum delay in milliseconds before processing a batch.
 * @param autoStart Whether the processor should start automatically on the first enqueue.
 * @param channelCapacity The capacity of the underlying channel.
 * @return A new [StreamBatcher] instance.
 */
@StreamCoreApi
fun <T> StreamBatcher(
    scope: CoroutineScope,
    batchSize: Int = 10,
    initialDelayMs: Long = 100L,
    maxDelayMs: Long = 1_000L,
    autoStart: Boolean = true,
    channelCapacity: Int = Channel.UNLIMITED,
): StreamBatcher<T> =
    StreamBatcherImpl<T>(
        scope = scope,
        batchSize = batchSize,
        initialDelayMs = initialDelayMs,
        maxDelayMs = maxDelayMs,
        autoStart = autoStart,
        channelCapacity = channelCapacity,
    )
