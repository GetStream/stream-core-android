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
 * A collection of events grouped by their type, produced by [StreamEventAggregator] during a
 * traffic spike.
 *
 * When the aggregator detects high event throughput, it collects events within a time window and
 * delivers them as a single [StreamAggregatedEvent] instead of dispatching each individually. This
 * allows product SDKs to apply one state update and one UI recomposition per window instead of one
 * per event.
 *
 * During normal (low) traffic, events flow through individually — this class is only used during
 * spikes.
 *
 * ### Usage
 *
 * ```kotlin
 * when (event) {
 *     is StreamAggregatedEvent<*> -> {
 *         event.events.forEach { (type, eventsOfType) ->
 *             when (type) {
 *                 "channel.updated" -> applyLatest(eventsOfType)
 *                 "message.new" -> processAll(eventsOfType)
 *             }
 *         }
 *     }
 *     else -> handleSingleEvent(event)
 * }
 * ```
 *
 * @param T The type of the individual events.
 * @property events Events grouped by type string. Each list preserves arrival order.
 */
@StreamInternalApi public class StreamAggregatedEvent<T>(public val events: Map<String, List<T>>)
