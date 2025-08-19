/*
 * Copyright (c) 2014-2025 Stream.io Inc. All rights reserved.
 *
 * Licensed under the Stream License;
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://github.com/GetStream/stream-android-base/blob/main/LICENSE
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.getstream.android.core.api.model.config

import io.getstream.android.core.annotations.StreamCoreApi
import io.getstream.android.core.annotations.StreamDsl
import io.getstream.android.core.api.authentication.StreamTokenManager
import io.getstream.android.core.api.authentication.StreamTokenProvider
import io.getstream.android.core.api.log.StreamLoggerProvider
import io.getstream.android.core.api.model.value.StreamApiKey
import io.getstream.android.core.api.model.value.StreamUserId
import io.getstream.android.core.api.processing.StreamDebounceMessageProcessor
import io.getstream.android.core.api.processing.StreamRetryProcessor
import io.getstream.android.core.api.processing.StreamSerialProcessingQueue
import io.getstream.android.core.api.processing.StreamSingleFlightProcessor
import io.getstream.android.core.api.serialization.StreamJsonSerialization
import io.getstream.android.core.api.socket.StreamConnectionIdHolder
import io.getstream.android.core.api.socket.StreamWebSocketFactory
import io.getstream.android.core.api.socket.listeners.StreamClientListener
import io.getstream.android.core.api.socket.listeners.StreamWebSocketListener
import io.getstream.android.core.api.socket.monitor.StreamHealthMonitor
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager
import okhttp3.Interceptor

/**
 * Aggregates all configuration required to create a
 * [StreamClient][io.getstream.android.core.api.StreamClient].
 *
 * This is a DSL-friendly container that groups concerns into logical sections: [logging], [http],
 * [socket], [processing], and [serialization]. Any field left as `null` (or empty) will be filled
 * with sensible defaults by the client factory.
 *
 * Typical usage:
 * ```
 * val cfg = StreamClientConfig(
 *   apiKey = StreamApiKey("key"),
 *   userId = StreamUserId("user"),
 *   endpoint = StreamEndpointConfig(...),
 *   tokenProvider = myTokenProvider
 * ).apply {
 *   logging = logging.copy(loggerProvider = myLogger)
 *   http = http.copy(interceptors = listOf(myInterceptor))
 * }
 * ```
 *
 * @property apiKey API key used to authenticate requests to Stream.
 * @property userId The ID of the user who will connect with this client.
 * @property endpoint Endpoint configuration (REST + WebSocket base URLs, headers, etc).
 * @property tokenProvider Provider used to fetch user JWTs on demand.
 * @property logging Logging-related configuration (logger provider).
 * @property http HTTP-layer configuration (interceptors, connectionId propagation).
 * @property socket WebSocket-layer configuration (factory, health monitor, subscriptions).
 * @property processing Queues/processors used internally (single-flight, serial queue, retry).
 * @property serialization JSON serialization configuration.
 * @property tokenManager Optional pre-built [StreamTokenManager]. If null, one is created by the
 *   factory using [tokenProvider].
 */
@StreamCoreApi
@StreamDsl
data class StreamClientConfig(
    val apiKey: StreamApiKey,
    val userId: StreamUserId,
    val endpoint: StreamEndpointConfig,
    var tokenProvider: StreamTokenProvider,
    var logging: Logging = Logging(),
    var http: Http = Http(),
    var socket: Socket = Socket(),
    var processing: Processing = Processing(),
    var serialization: Serialization = Serialization(),
    var tokenManager: StreamTokenManager? = null,
) {

    /**
     * JSON serialization settings used by the client for encoding/decoding models and events.
     *
     * If [json] is `null`, a default implementation (e.g., Moshi-backed) will be created.
     *
     * @property json Optional JSON serialization engine override.
     */
    data class Serialization(val json: StreamJsonSerialization? = null)

    /**
     * Logging configuration.
     *
     * @property loggerProvider Provider for creating logger instances. Defaults to
     *   [StreamLoggerProvider.defaultAndroidLogger].
     */
    data class Logging(
        val loggerProvider: StreamLoggerProvider =
            StreamLoggerProvider.Companion.defaultAndroidLogger()
    )

    /**
     * HTTP configuration for REST calls made by the client.
     *
     * @property interceptors Additional OkHttp [Interceptor]s to be applied to the HTTP stack.
     * @property connectionIdHolder Optional holder used to inject the current connection id into
     *   request headers (if provided). If `null`, a default holder may be created elsewhere.
     */
    data class Http(
        val interceptors: List<Interceptor> = emptyList(),
        val connectionIdHolder: StreamConnectionIdHolder? = null,
    )

    /**
     * WebSocket configuration.
     *
     * @property connectionIdHolder Optional holder for the active connection id, used by WebSocket
     *   and HTTP integrations.
     * @property socketFactory Optional custom [StreamWebSocketFactory]. If `null`, a default
     *   factory will be used.
     * @property healthMonitor Optional [StreamHealthMonitor] to supervise the socket liveness. If
     *   `null`, a default implementation will be created.
     * @property socketSubscriptions Optional subscription manager for [StreamWebSocketListener]s.
     *   If `null`, a default manager will be used.
     * @property eventDebounce Optional debounce processor for inbound raw WebSocket messages. If
     *   `null`, a default processor will be used.
     */
    data class Socket(
        val connectionIdHolder: StreamConnectionIdHolder? = null,
        val socketFactory: StreamWebSocketFactory? = null,
        val healthMonitor: StreamHealthMonitor? = null,
        val socketSubscriptions: StreamSubscriptionManager<StreamWebSocketListener>? = null,
        val eventDebounce: StreamDebounceMessageProcessor<String>? = null,
    )

    /**
     * Internal processing components used by the client.
     *
     * You can inject custom implementations for testing or advanced tuning. Any component left as
     * `null` will be replaced with a default one.
     *
     * @property singleFlight Optional [StreamSingleFlightProcessor] to de-duplicate concurrent
     *   work.
     * @property serialQueue Optional [StreamSerialProcessingQueue] for serialized/background tasks.
     * @property retry Optional [StreamRetryProcessor] strategy for transient errors.
     * @property clientSubscriptions Optional subscription manager for [StreamClientListener]s.
     */
    data class Processing(
        val singleFlight: StreamSingleFlightProcessor? = null,
        val serialQueue: StreamSerialProcessingQueue? = null,
        val retry: StreamRetryProcessor? = null,
        val clientSubscriptions: StreamSubscriptionManager<StreamClientListener>? = null,
    )
}
