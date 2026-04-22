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

package io.getstream.android.core.api.model.config

import io.getstream.android.core.annotations.StreamInternalApi
import io.getstream.android.core.api.model.value.StreamApiKey
import io.getstream.android.core.api.model.value.StreamHttpClientInfoHeader
import io.getstream.android.core.api.model.value.StreamWsUrl

/**
 * Configuration for the Stream WebSocket connection.
 *
 * Holds both **identity** (URL, API key, auth type) and **operational** tunables (health check
 * timing, event aggregation, connection timeout). Products pass this to the [StreamClient] factory
 * to describe their socket.
 *
 * ### Usage
 *
 * ```kotlin
 * // Coordinator socket — standard timing
 * val coordinatorSocket = StreamSocketConfig.jwt(
 *     url = StreamWsUrl.fromString("wss://chat.stream-io-api.com/connect"),
 *     apiKey = apiKey,
 *     clientInfoHeader = clientInfo,
 * )
 *
 * // SFU socket — aggressive timing, low aggregation threshold
 * val sfuSocket = StreamSocketConfig.jwt(
 *     url = StreamWsUrl.fromString("wss://sfu.stream-io-api.com"),
 *     apiKey = apiKey,
 *     clientInfoHeader = clientInfo,
 *     healthCheckIntervalMs = 5_000,
 *     livenessThresholdMs = 15_000,
 *     connectionTimeoutMs = 2_000,
 *     aggregationThreshold = 10,
 *     aggregationMaxWindowMs = 200,
 * )
 * ```
 *
 * @param url WebSocket endpoint URL.
 * @param apiKey Stream API key for authentication.
 * @param authType Authentication type (e.g., "jwt", "anonymous").
 * @param clientInfoHeader X-Stream-Client header value.
 * @param healthCheckIntervalMs Interval between health check pings in milliseconds.
 * @param livenessThresholdMs Time without a health check ack before the connection is considered
 *   unhealthy in milliseconds.
 * @param connectionTimeoutMs WebSocket connection timeout in milliseconds.
 * @param aggregationThreshold Number of accumulated events that triggers aggregated delivery
 *   instead of individual dispatch.
 * @param aggregationMaxWindowMs Maximum time the aggregator collects events before delivering.
 *   This is the latency ceiling in milliseconds.
 * @param aggregationDispatchQueueCapacity Bounded capacity of the dispatch queue between the
 *   aggregator's collector and dispatcher coroutines.
 */
@Suppress("LongParameterList")
@StreamInternalApi
@ConsistentCopyVisibility
public data class StreamSocketConfig
private constructor(
    val url: StreamWsUrl,
    val apiKey: StreamApiKey,
    val authType: String,
    val clientInfoHeader: StreamHttpClientInfoHeader,
    val healthCheckIntervalMs: Long = DEFAULT_HEALTH_INTERVAL_MS,
    val livenessThresholdMs: Long = DEFAULT_LIVENESS_MS,
    val connectionTimeoutMs: Long = DEFAULT_CONNECTION_TIMEOUT_MS,
    val aggregationThreshold: Int = DEFAULT_AGGREGATION_THRESHOLD,
    val aggregationMaxWindowMs: Long = DEFAULT_AGGREGATION_MAX_WINDOW_MS,
    val aggregationDispatchQueueCapacity: Int = DEFAULT_AGGREGATION_DISPATCH_QUEUE_CAPACITY,
) {
    /** Default values for [StreamSocketConfig] fields. */
    public companion object {
        private const val JWT_AUTH_TYPE = "jwt"
        private const val ANONYMOUS_AUTH_TYPE = "anonymous"

        /** Default health check ping interval: 25 seconds. */
        public const val DEFAULT_HEALTH_INTERVAL_MS: Long = 25_000L

        /** Default liveness threshold: 60 seconds without ack. */
        public const val DEFAULT_LIVENESS_MS: Long = 60_000L

        /** Default connection timeout: 10 seconds. */
        public const val DEFAULT_CONNECTION_TIMEOUT_MS: Long = 10_000L

        /** Default aggregation threshold: 50 events trigger aggregated delivery. */
        public const val DEFAULT_AGGREGATION_THRESHOLD: Int = 50

        /** Default aggregation max window: 500ms latency ceiling. */
        public const val DEFAULT_AGGREGATION_MAX_WINDOW_MS: Long = 500L

        /** Default dispatch queue capacity: 16 items. */
        public const val DEFAULT_AGGREGATION_DISPATCH_QUEUE_CAPACITY: Int = 16

        /**
         * Creates a JWT-based [StreamSocketConfig].
         *
         * @param url WebSocket endpoint URL.
         * @param apiKey Stream API key for authentication.
         * @param clientInfoHeader X-Stream-Client header value.
         * @param healthCheckIntervalMs Interval between health check pings in milliseconds.
         * @param livenessThresholdMs Liveness threshold in milliseconds.
         * @param connectionTimeoutMs WebSocket connection timeout in milliseconds.
         * @param aggregationThreshold Events before aggregated delivery triggers.
         * @param aggregationMaxWindowMs Maximum collection window in milliseconds.
         * @param aggregationDispatchQueueCapacity Dispatch queue capacity.
         * @return A JWT-based [StreamSocketConfig].
         */
        @Suppress("LongParameterList")
        public fun jwt(
            url: StreamWsUrl,
            apiKey: StreamApiKey,
            clientInfoHeader: StreamHttpClientInfoHeader,
            healthCheckIntervalMs: Long = DEFAULT_HEALTH_INTERVAL_MS,
            livenessThresholdMs: Long = DEFAULT_LIVENESS_MS,
            connectionTimeoutMs: Long = DEFAULT_CONNECTION_TIMEOUT_MS,
            aggregationThreshold: Int = DEFAULT_AGGREGATION_THRESHOLD,
            aggregationMaxWindowMs: Long = DEFAULT_AGGREGATION_MAX_WINDOW_MS,
            aggregationDispatchQueueCapacity: Int = DEFAULT_AGGREGATION_DISPATCH_QUEUE_CAPACITY,
        ): StreamSocketConfig =
            StreamSocketConfig(
                url = url,
                apiKey = apiKey,
                authType = JWT_AUTH_TYPE,
                clientInfoHeader = clientInfoHeader,
                healthCheckIntervalMs = healthCheckIntervalMs,
                livenessThresholdMs = livenessThresholdMs,
                connectionTimeoutMs = connectionTimeoutMs,
                aggregationThreshold = aggregationThreshold,
                aggregationMaxWindowMs = aggregationMaxWindowMs,
                aggregationDispatchQueueCapacity = aggregationDispatchQueueCapacity,
            )

        /**
         * Creates an anonymous [StreamSocketConfig].
         *
         * @param url WebSocket endpoint URL.
         * @param apiKey Stream API key for authentication.
         * @param clientInfoHeader X-Stream-Client header value.
         * @param healthCheckIntervalMs Interval between health check pings in milliseconds.
         * @param livenessThresholdMs Liveness threshold in milliseconds.
         * @param connectionTimeoutMs WebSocket connection timeout in milliseconds.
         * @param aggregationThreshold Events before aggregated delivery triggers.
         * @param aggregationMaxWindowMs Maximum collection window in milliseconds.
         * @param aggregationDispatchQueueCapacity Dispatch queue capacity.
         * @return An anonymous [StreamSocketConfig].
         */
        @Suppress("LongParameterList")
        public fun anonymous(
            url: StreamWsUrl,
            apiKey: StreamApiKey,
            clientInfoHeader: StreamHttpClientInfoHeader,
            healthCheckIntervalMs: Long = DEFAULT_HEALTH_INTERVAL_MS,
            livenessThresholdMs: Long = DEFAULT_LIVENESS_MS,
            connectionTimeoutMs: Long = DEFAULT_CONNECTION_TIMEOUT_MS,
            aggregationThreshold: Int = DEFAULT_AGGREGATION_THRESHOLD,
            aggregationMaxWindowMs: Long = DEFAULT_AGGREGATION_MAX_WINDOW_MS,
            aggregationDispatchQueueCapacity: Int = DEFAULT_AGGREGATION_DISPATCH_QUEUE_CAPACITY,
        ): StreamSocketConfig =
            StreamSocketConfig(
                url = url,
                apiKey = apiKey,
                authType = ANONYMOUS_AUTH_TYPE,
                clientInfoHeader = clientInfoHeader,
                healthCheckIntervalMs = healthCheckIntervalMs,
                livenessThresholdMs = livenessThresholdMs,
                connectionTimeoutMs = connectionTimeoutMs,
                aggregationThreshold = aggregationThreshold,
                aggregationMaxWindowMs = aggregationMaxWindowMs,
                aggregationDispatchQueueCapacity = aggregationDispatchQueueCapacity,
            )

        /**
         * Creates a custom [StreamSocketConfig].
         *
         * @param url WebSocket endpoint URL.
         * @param apiKey Stream API key for authentication.
         * @param authType Authentication type (e.g., "jwt", "anonymous").
         * @param clientInfoHeader X-Stream-Client header value.
         * @param healthCheckIntervalMs Interval between health check pings in milliseconds.
         * @param livenessThresholdMs Liveness threshold in milliseconds.
         * @param connectionTimeoutMs WebSocket connection timeout in milliseconds.
         * @param aggregationThreshold Events before aggregated delivery triggers.
         * @param aggregationMaxWindowMs Maximum collection window in milliseconds.
         * @param aggregationDispatchQueueCapacity Dispatch queue capacity.
         * @return A custom [StreamSocketConfig].
         */
        @Suppress("LongParameterList")
        public fun custom(
            url: StreamWsUrl,
            apiKey: StreamApiKey,
            authType: String,
            clientInfoHeader: StreamHttpClientInfoHeader,
            healthCheckIntervalMs: Long = DEFAULT_HEALTH_INTERVAL_MS,
            livenessThresholdMs: Long = DEFAULT_LIVENESS_MS,
            connectionTimeoutMs: Long = DEFAULT_CONNECTION_TIMEOUT_MS,
            aggregationThreshold: Int = DEFAULT_AGGREGATION_THRESHOLD,
            aggregationMaxWindowMs: Long = DEFAULT_AGGREGATION_MAX_WINDOW_MS,
            aggregationDispatchQueueCapacity: Int = DEFAULT_AGGREGATION_DISPATCH_QUEUE_CAPACITY,
        ): StreamSocketConfig {
            require(authType.isNotBlank()) { "Auth type must not be blank" }
            return StreamSocketConfig(
                url = url,
                apiKey = apiKey,
                authType = authType,
                clientInfoHeader = clientInfoHeader,
                healthCheckIntervalMs = healthCheckIntervalMs,
                livenessThresholdMs = livenessThresholdMs,
                connectionTimeoutMs = connectionTimeoutMs,
                aggregationThreshold = aggregationThreshold,
                aggregationMaxWindowMs = aggregationMaxWindowMs,
                aggregationDispatchQueueCapacity = aggregationDispatchQueueCapacity,
            )
        }
    }
}
