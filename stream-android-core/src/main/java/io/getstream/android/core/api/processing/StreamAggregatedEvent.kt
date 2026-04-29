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

/**
 * A batch of events produced by [StreamEventAggregator] during a traffic spike.
 *
 * When the aggregator detects high event throughput, it collects events within a time window and
 * delivers them as a single [StreamAggregatedEvent] instead of dispatching each individually. This
 * allows product SDKs to apply all updates sequentially in one atomic state update and one UI
 * recomposition per window instead of one per event.
 *
 * Events are stored in arrival order. Product SDKs should process them sequentially to maintain
 * correctness (e.g. "reaction added" must be processed before "reaction removed" for the same
 * entity).
 *
 * During normal (low) traffic, events flow through individually — this class is only used during
 * spikes.
 *
 * ### Usage
 *
 * ```kotlin
 * when (event) {
 *     is StreamAggregatedEvent<*> -> {
 *         event.events.forEach { singleEvent ->
 *             handleSingleEvent(singleEvent)
 *         }
 *     }
 *     else -> handleSingleEvent(event)
 * }
 * ```
 *
 * @param T The type of the individual events.
 * @property events Events in arrival order. Process sequentially to maintain correctness.
 */
@StreamInternalApi
public class StreamAggregatedEvent<T>(events: List<T>) {
    public val events: List<T> = events.toList()
}
