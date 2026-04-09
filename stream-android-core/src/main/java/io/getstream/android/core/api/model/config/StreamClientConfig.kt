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

package io.getstream.android.core.api.model.config

import io.getstream.android.core.annotations.StreamInternalApi
import io.getstream.android.core.api.log.StreamLoggerProvider
import io.getstream.android.core.api.model.value.StreamWsUrl

/**
 * Configuration for tuning [StreamClient][io.getstream.android.core.api.StreamClient] behaviour.
 *
 * All fields have sensible defaults. Pass an instance only when you need to override something.
 *
 * ### Usage
 *
 * ```kotlin
 * // All defaults — production, standard timing
 * val client = StreamClient(context, apiKey, user, tokenProvider, ...)
 *
 * // Tuned for staging with slower health checks
 * val client = StreamClient(
 *     ...,
 *     config = StreamClientConfig(
 *         wsUrl = StreamWsUrl.fromString("wss://staging.getstream.io"),
 *         healthCheckIntervalMs = 30_000,
 *     ),
 * )
 * ```
 *
 * @param wsUrl WebSocket endpoint URL. Defaults to production.
 * @param logProvider Logger provider. Defaults to Android logcat.
 * @param healthCheckIntervalMs Interval between health check pings in milliseconds. Defaults to
 *   25000ms.
 * @param livenessThresholdMs Time without a health check ack before the connection is considered
 *   unhealthy in milliseconds. Defaults to 60000ms.
 * @param batchSize Maximum number of WebSocket messages to batch before flushing. Defaults to 10.
 * @param batchInitialDelayMs Initial debounce window for batching in milliseconds. Defaults to
 *   100ms.
 * @param batchMaxDelayMs Maximum debounce window for batching in milliseconds. Defaults to 1000ms.
 * @param httpConfig Optional HTTP client customization (OkHttp builder, interceptors).
 * @param serializationConfig Optional override for JSON and event serialization. When null, the
 *   factory builds a default config from the provided [productEventSerializer].
 */
@Suppress("LongParameterList")
@StreamInternalApi
public data class StreamClientConfig(
    val wsUrl: StreamWsUrl? = null,
    val logProvider: StreamLoggerProvider = StreamLoggerProvider.defaultAndroidLogger(),
    val healthCheckIntervalMs: Long = DEFAULT_HEALTH_INTERVAL_MS,
    val livenessThresholdMs: Long = DEFAULT_LIVENESS_MS,
    val batchSize: Int = DEFAULT_BATCH_SIZE,
    val batchInitialDelayMs: Long = DEFAULT_BATCH_INIT_DELAY_MS,
    val batchMaxDelayMs: Long = DEFAULT_BATCH_MAX_DELAY_MS,
    val httpConfig: StreamHttpConfig? = null,
    val serializationConfig: StreamClientSerializationConfig? = null,
) {
    /** Default values for [StreamClientConfig] fields. */
    public companion object {
        /** Default health check ping interval: 25 seconds. */
        public const val DEFAULT_HEALTH_INTERVAL_MS: Long = 25_000L

        /** Default liveness threshold: 60 seconds without ack. */
        public const val DEFAULT_LIVENESS_MS: Long = 60_000L

        /** Default batch size: 10 messages. */
        public const val DEFAULT_BATCH_SIZE: Int = 10

        /** Default initial batch delay: 100ms. */
        public const val DEFAULT_BATCH_INIT_DELAY_MS: Long = 100L

        /** Default max batch delay: 1 second. */
        public const val DEFAULT_BATCH_MAX_DELAY_MS: Long = 1_000L
    }
}
