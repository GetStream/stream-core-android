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
 * @param policy Aggregation policy defining type extraction, deserialization, and tuning
 *   (thresholds, windows, queue capacity). Validated at construction — once you have a policy, it
 *   is guaranteed valid.
 * @param inboxCapacity Capacity of the raw event inbox channel.
 * @param logger Optional tagged logger for diagnostics.
 */
@StreamInternalApi
public fun <T> StreamEventAggregator(
    scope: CoroutineScope,
    policy: StreamEventAggregationPolicy<T>,
    inboxCapacity: Int = Channel.UNLIMITED,
    logger: StreamLogger? = null,
): StreamEventAggregator<T> =
    StreamEventAggregatorImpl(
        scope = scope,
        typeExtractor = policy.extractType,
        deserializer = policy.deserialize,
        aggregationThreshold = policy.aggregationThreshold,
        maxWindowMs = policy.maxWindowMs,
        dispatchQueueCapacity = policy.dispatchQueueCapacity,
        inboxCapacity = inboxCapacity,
        logger = logger,
    )
