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
import io.getstream.android.core.internal.processing.StreamEventAggregatorImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel

/**
 * Adaptive event aggregator that switches between individual and aggregated event delivery based on
 * traffic volume.
 *
 * ### Architecture
 *
 * Two decoupled coroutines:
 * - **Collector:** Drains the inbox channel, groups events by type, and packages them into the
 *   dispatch queue. Collection ends when either [aggregationThreshold] is reached or [maxWindowMs]
 *   elapses — whichever comes first. This guarantees a latency ceiling regardless of downstream
 *   processing speed.
 * - **Dispatcher:** Takes packaged work from the dispatch queue and invokes the registered handler.
 *   Runs sequentially — one delivery at a time, preserving order.
 *
 * ### Adaptive behavior
 * - **Low traffic:** When the collector drains the channel and finds few events (below
 *   [aggregationThreshold]), each event is dispatched individually via the handler.
 * - **Spike:** When accumulated events reach or exceed [aggregationThreshold], they are grouped by
 *   type into a [StreamAggregatedEvent] and dispatched as a single call.
 *
 * ### Backpressure
 * - Natural: While the dispatcher is busy, events accumulate in the inbox channel. The next
 *   collection cycle finds them ready — larger accumulation triggers aggregation automatically.
 * - The dispatch queue has a bounded capacity. If the dispatcher can't keep up, the collector logs
 *   a warning when the queue is full.
 *
 * @param T The type of the deserialized event.
 */
@StreamInternalApi
public interface StreamEventAggregator<T> {

    /**
     * Starts the collector and dispatcher coroutines.
     *
     * @return `Result.success(Unit)` if started successfully.
     */
    public fun start(): Result<Unit>

    /**
     * Enqueues a raw event for processing. Non-suspending, suitable for WebSocket callbacks.
     *
     * Returns `false` if the aggregator has not been [start]ed or if the inbox is full/closed.
     *
     * @param raw The raw event (typically a JSON string from the WebSocket).
     * @return `true` if accepted into the inbox, `false` otherwise.
     */
    public fun offer(raw: String): Boolean

    /**
     * Registers the handler invoked for each delivery.
     *
     * The handler receives **either**:
     * - An individual event `T` (low traffic), or
     * - A [StreamAggregatedEvent]`<T>` wrapping grouped events (spike).
     *
     * Product SDKs distinguish using `is StreamAggregatedEvent<*>`.
     *
     * @param handler A suspending function receiving the event or aggregated event.
     */
    public fun onEvent(handler: suspend (Any) -> Unit)

    /**
     * Stops both coroutines and releases resources. Idempotent.
     *
     * @return `Result.success(Unit)` on clean shutdown.
     */
    public fun stop(): Result<Unit>
}

/**
 * Creates a new [StreamEventAggregator] instance.
 *
 * @param T The deserialized event type.
 * @param scope Coroutine scope for the collector and dispatcher coroutines.
 * @param typeExtractor Extracts the event type string from a raw JSON message. Returning `null`
 *   means the type could not be determined — the event is grouped under an empty key.
 * @param deserializer Deserializes a raw JSON string into `T`. Returns `Result.failure` on parse
 *   errors.
 * @param aggregationThreshold Number of accumulated events that triggers aggregated delivery
 *   instead of individual dispatch.
 * @param maxWindowMs Maximum time (milliseconds) the collector will wait before packaging and
 *   delivering whatever has accumulated. This is the latency ceiling.
 * @param dispatchQueueCapacity Bounded capacity of the dispatch queue between collector and
 *   dispatcher. When full, the collector logs a warning.
 * @param inboxCapacity Capacity of the raw event inbox channel.
 */
@StreamInternalApi
public fun <T> StreamEventAggregator(
    scope: CoroutineScope,
    typeExtractor: (String) -> String?,
    deserializer: (String) -> Result<T>,
    aggregationThreshold: Int = 50,
    maxWindowMs: Long = 500L,
    dispatchQueueCapacity: Int = 16,
    inboxCapacity: Int = Channel.UNLIMITED,
    logger: StreamLogger? = null,
): StreamEventAggregator<T> {
    require(aggregationThreshold > 0) { "aggregationThreshold must be > 0" }
    require(maxWindowMs > 0) { "maxWindowMs must be > 0" }
    require(dispatchQueueCapacity > 0) { "dispatchQueueCapacity must be > 0" }

    return StreamEventAggregatorImpl(
        scope = scope,
        typeExtractor = typeExtractor,
        deserializer = deserializer,
        aggregationThreshold = aggregationThreshold,
        maxWindowMs = maxWindowMs,
        dispatchQueueCapacity = dispatchQueueCapacity,
        inboxCapacity = inboxCapacity,
        logger = logger,
    )
}
