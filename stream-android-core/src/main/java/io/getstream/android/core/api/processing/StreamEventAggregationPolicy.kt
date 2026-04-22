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
 * Complete aggregation configuration for [StreamEventAggregator].
 *
 * Combines **behavior** (how to extract types and deserialize) with **tuning** (thresholds,
 * windows, queue capacity). Once created via a factory method, the policy is guaranteed valid — all
 * invariants are checked at construction time.
 *
 * The primary constructor is **private**. Create instances through the [from] factory method.
 *
 * ### Invariants
 * * `aggregationThreshold > 0`
 * * `maxWindowMs > 0`
 * * `dispatchQueueCapacity > 0`
 *
 * @param T The deserialized event type.
 * @param extractType Extracts the event type string from a raw message (typically JSON). Returns
 *   `null` if the type cannot be determined — events with unknown type are grouped under an empty
 *   key.
 * @param deserialize Deserializes a raw message into `T`. Returns `Result.failure` on parse errors.
 * @param aggregationThreshold Number of accumulated events that triggers aggregated delivery
 *   instead of individual dispatch.
 * @param maxWindowMs Maximum time (milliseconds) the collector will wait before packaging and
 *   delivering whatever has accumulated. This is the latency ceiling.
 * @param dispatchQueueCapacity Bounded capacity of the dispatch queue between collector and
 *   dispatcher. When full, the collector logs a warning.
 */
@StreamInternalApi
@ConsistentCopyVisibility
public data class StreamEventAggregationPolicy<T>
private constructor(
    val extractType: (raw: String) -> String?,
    val deserialize: (raw: String) -> Result<T>,
    val aggregationThreshold: Int,
    val maxWindowMs: Long,
    val dispatchQueueCapacity: Int,
) {
    public companion object {

        /** Default aggregation threshold: 50 events trigger aggregated delivery. */
        public const val DEFAULT_AGGREGATION_THRESHOLD: Int = 50

        /** Default aggregation max window: 500ms latency ceiling. */
        public const val DEFAULT_MAX_WINDOW_MS: Long = 500L

        /** Default dispatch queue capacity: 16 items. */
        public const val DEFAULT_DISPATCH_QUEUE_CAPACITY: Int = 16

        /**
         * Creates a validated policy from the given parameters.
         *
         * ### Usage
         *
         * ```kotlin
         * val policy = StreamEventAggregationPolicy.from(
         *     typeExtractor = { raw -> eventParser.peekType(raw) },
         *     deserializer = { raw -> eventParser.deserialize(raw) },
         *     aggregationThreshold = 100,
         *     maxWindowMs = 300,
         * )
         * ```
         *
         * @param T The deserialized event type.
         * @param typeExtractor Extracts the event type from a raw message.
         * @param deserializer Deserializes a raw message into `T`.
         * @param aggregationThreshold Events before aggregated delivery triggers.
         * @param maxWindowMs Maximum collection window in milliseconds.
         * @param dispatchQueueCapacity Dispatch queue capacity.
         * @return A validated [StreamEventAggregationPolicy].
         * @throws IllegalArgumentException if any numeric parameter is ≤ 0.
         */
        @Suppress("LongParameterList")
        public fun <T> from(
            typeExtractor: (raw: String) -> String?,
            deserializer: (raw: String) -> Result<T>,
            aggregationThreshold: Int = DEFAULT_AGGREGATION_THRESHOLD,
            maxWindowMs: Long = DEFAULT_MAX_WINDOW_MS,
            dispatchQueueCapacity: Int = DEFAULT_DISPATCH_QUEUE_CAPACITY,
        ): StreamEventAggregationPolicy<T> {
            validate(aggregationThreshold, maxWindowMs, dispatchQueueCapacity)
            return StreamEventAggregationPolicy(
                extractType = typeExtractor,
                deserialize = deserializer,
                aggregationThreshold = aggregationThreshold,
                maxWindowMs = maxWindowMs,
                dispatchQueueCapacity = dispatchQueueCapacity,
            )
        }

        private fun validate(
            aggregationThreshold: Int,
            maxWindowMs: Long,
            dispatchQueueCapacity: Int,
        ) {
            require(aggregationThreshold > 0) {
                "aggregationThreshold must be > 0, was $aggregationThreshold"
            }
            require(maxWindowMs > 0) { "maxWindowMs must be > 0, was $maxWindowMs" }
            require(dispatchQueueCapacity > 0) {
                "dispatchQueueCapacity must be > 0, was $dispatchQueueCapacity"
            }
        }
    }
}
