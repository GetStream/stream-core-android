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
package io.getstream.android.core.api

import io.getstream.android.core.annotations.StreamInternalApi
import io.getstream.android.core.api.authentication.StreamTokenManager
import io.getstream.android.core.api.authentication.StreamTokenProvider
import io.getstream.android.core.api.http.StreamOkHttpInterceptors
import io.getstream.android.core.api.log.StreamLoggerProvider
import io.getstream.android.core.api.model.config.StreamClientSerializationConfig
import io.getstream.android.core.api.model.config.StreamHttpConfig
import io.getstream.android.core.api.model.config.StreamSocketConfig
import io.getstream.android.core.api.model.connection.StreamConnectedUser
import io.getstream.android.core.api.model.connection.StreamConnectionState
import io.getstream.android.core.api.model.connection.network.StreamNetworkInfo
import io.getstream.android.core.api.model.value.StreamApiKey
import io.getstream.android.core.api.model.value.StreamHttpClientInfoHeader
import io.getstream.android.core.api.model.value.StreamUserId
import io.getstream.android.core.api.model.value.StreamWsUrl
import io.getstream.android.core.api.observers.network.StreamNetworkMonitor
import io.getstream.android.core.api.processing.StreamBatcher
import io.getstream.android.core.api.processing.StreamRetryProcessor
import io.getstream.android.core.api.processing.StreamSerialProcessingQueue
import io.getstream.android.core.api.processing.StreamSingleFlightProcessor
import io.getstream.android.core.api.serialization.StreamEventSerialization
import io.getstream.android.core.api.socket.StreamConnectionIdHolder
import io.getstream.android.core.api.socket.StreamWebSocket
import io.getstream.android.core.api.socket.StreamWebSocketFactory
import io.getstream.android.core.api.socket.listeners.StreamClientListener
import io.getstream.android.core.api.socket.monitor.StreamHealthMonitor
import io.getstream.android.core.api.subscribe.StreamSubscription
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager
import io.getstream.android.core.internal.client.StreamClientImpl
import io.getstream.android.core.internal.serialization.StreamCompositeEventSerializationImpl
import io.getstream.android.core.internal.serialization.StreamCompositeMoshiJsonSerialization
import io.getstream.android.core.internal.serialization.StreamMoshiJsonSerializationImpl
import io.getstream.android.core.internal.serialization.moshi.StreamCoreMoshiProvider
import io.getstream.android.core.internal.socket.StreamSocketSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Entry point for establishing and managing a connection to Stream services.
 *
 * ### Semantics
 * - Exposes connection-related state via [connectionState] (hot, read-only).
 * - Implementations may perform network I/O and authentication during [connect].
 *
 * ### Threading
 * - All APIs are safe to call from any thread.
 * - [connect] and [disconnect] are `suspend` and must not block the caller thread.
 *
 * ### Lifecycle
 * - Create → [connect] → use → [disconnect].
 * - After [disconnect], resources are released; [connectionState] continues to reflect the latest
 *   status.
 *
 * ### Errors & cancellation
 * - Methods return `Result<…>`: `success` on completion, `failure` on error.
 * - If the *caller* is cancelled while awaiting, a [kotlinx.coroutines.CancellationException] is
 *   thrown.
 *
 * ### Usage
 *
 * ```kotlin
 * // Use
 * val result = client.connect()
 * if (result.isSuccess) {
 *     // Observe connection state
 *     // Connected...
 * } else {
 *     val cause = result.exceptionOrNull()
 *     // handle error
 * }
 *
 * // Observe exposed client state
 * client.state.collect {
 *   // Get state updates...
 * }
 *
 * // …
 * client.disconnect()
 * ```
 */
@StreamInternalApi
public interface StreamClient {
    /**
     * Read-only, hot state holder for this client.
     *
     * **Semantics**
     * - Emits connection status changes (e.g., connecting/connected/disconnected).
     * - Hot & conflated: new collectors receive the latest value immediately.
     */
    public val connectionState: StateFlow<StreamConnectionState>

    /**
     * Read-only, hot state holder for the current network snapshot.
     *
     * **Semantics**
     * - Emits the latest network snapshot whenever it changes.
     * - Hot & conflated: new collectors receive the latest value immediately.
     * - `null` if no network is available.
     */
    @StreamInternalApi public val networkInfo: StateFlow<StreamNetworkInfo.Snapshot?>

    /**
     * Establishes a connection for the current user.
     *
     * **Result contract**
     * - `Result.success(StreamConnectedUser)` on success.
     * - `Result.failure(cause)` if the connection fails.
     *
     * **Cancellation**
     * - Throws [kotlinx.coroutines.CancellationException] if the awaiting coroutine is cancelled.
     */
    public suspend fun connect(): Result<StreamConnectedUser>

    /**
     * Terminates the active connection and releases related resources.
     *
     * **Result contract**
     * - `Result.success(Unit)` on a clean shutdown.
     * - `Result.failure(cause)` if teardown fails.
     *
     * **Cancellation**
     * - Throws [kotlinx.coroutines.CancellationException] if the awaiting coroutine is cancelled.
     */
    public suspend fun disconnect(): Result<Unit>

    /**
     * Subscribes to client events and state
     *
     * @param listener The listener to subscribe.
     */
    public fun subscribe(listener: StreamClientListener): Result<StreamSubscription>
}

/**
 * ### Overview
 *
 * Creates a [StreamClient] with the given [apiKey], [userId], [tokenProvider] and [scope]. The
 * client is created in a disconnected state. You must call `connect()` to establish a connection.
 * The client is automatically disconnected when the [scope] is cancelled.
 *
 * **Important**: The client instance **must be kept alive for the duration of the connection**. Do
 * not create a new client for every operation.
 *
 * **Token provider:**
 * - The [tokenProvider] is used to fetch tokens on demand. The first token is cached internally.
 *   When the first request needs to be made, the token is fetched from the provider. If you already
 *   have a token, you can cache it in your provider and return it as a valid token in `loadToken`.
 *   See [StreamTokenProvider] for more details.
 *
 * **Scope:**
 * - The [scope] is used to launch the client's internal coroutines. It is recommended to use a
 *   `CoroutineScope(SupervisorJob() + Dispatchers.Default)` for this purpose.
 *
 * ### Security
 * - The [tokenProvider] is used to fetch tokens on demand. The first token is cached internally.
 *   When the token expires, the provider is called again to fetch a new one.
 * - The expiration is determined by a `401` response from the server at which point the request is
 *   retried with the new token.
 *
 * ### Performance
 * - The client uses a single-flight pattern to deduplicate concurrent requests.
 * - The client uses a serial processing queue to ensure that requests are executed in order.
 * - The client uses a message batcher to coalesce high-frequency events.
 *
 * ### Usage
 *
 * ```kotlin
 * val client = StreamClient(
 *     apiKey = "my-api-key",
 *     userId = "my-user-id",
 *     tokenProvider = MyTokenProvider(),
 *     scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
 * )
 * ```
 *
 * @param apiKey The API key.
 * @param userId The user ID.
 * @param wsUrl The WebSocket URL.
 * @param clientInfoHeader The client info header.
 * @param tokenProvider The token provider.
 * @param scope The coroutine scope.
 * @param logProvider The logger provider.
 * @param clientSubscriptionManager The client subscription manager.
 * @param tokenManager The token manager.
 * @param singleFlight The single-flight processor.
 * @param serialQueue The serial processing queue.
 * @param httpConfig The HTTP configuration.
 * @param retryProcessor The retry processor.
 * @param connectionIdHolder The connection ID holder.
 * @param socketFactory The WebSocket factory.
 * @param healthMonitor The health monitor.
 * @param batcher The WebSocket event batcher.
 */
@StreamInternalApi
public fun StreamClient(
    // Client config
    apiKey: StreamApiKey,
    userId: StreamUserId,
    wsUrl: StreamWsUrl,
    products: List<String>,
    clientInfoHeader: StreamHttpClientInfoHeader,
    clientSubscriptionManager: StreamSubscriptionManager<StreamClientListener>,
    // Token
    tokenProvider: StreamTokenProvider,
    tokenManager: StreamTokenManager,
    // Processing
    singleFlight: StreamSingleFlightProcessor,
    serialQueue: StreamSerialProcessingQueue,
    retryProcessor: StreamRetryProcessor,
    scope: CoroutineScope,
    // Socket
    connectionIdHolder: StreamConnectionIdHolder,
    socketFactory: StreamWebSocketFactory,
    batcher: StreamBatcher<String>,
    // Monitoring
    healthMonitor: StreamHealthMonitor,
    networkMonitor: StreamNetworkMonitor,
    // Http
    httpConfig: StreamHttpConfig? = null,
    // Serialization
    serializationConfig: StreamClientSerializationConfig,
    // Logging
    logProvider: StreamLoggerProvider = StreamLoggerProvider.defaultAndroidLogger(),
): StreamClient {
    val clientLogger = logProvider.taggedLogger(tag = "SCClient")
    val parent = scope.coroutineContext[Job]
    val supervisorJob =
        if (parent != null) {
            SupervisorJob(parent)
        } else {
            SupervisorJob()
        }
    val clientScope = CoroutineScope(supervisorJob + scope.coroutineContext)

    val socket =
        StreamWebSocket(
            logger = logProvider.taggedLogger("SCSocket"),
            socketFactory = socketFactory,
            subscriptionManager =
                StreamSubscriptionManager(
                    logger = logProvider.taggedLogger("SCSocketSubscriptions")
                ),
        )
    val compositeSerialization =
        StreamCompositeMoshiJsonSerialization(
            logProvider.taggedLogger("SCSerialization"),
            StreamMoshiJsonSerializationImpl(StreamCoreMoshiProvider().builder {}.build()),
            serializationConfig.json,
        )

    httpConfig?.apply {
        if (automaticInterceptors) {
            httpBuilder.apply {
                addInterceptor(StreamOkHttpInterceptors.clientInfo(clientInfoHeader))
                addInterceptor(StreamOkHttpInterceptors.apiKey(apiKey))
                addInterceptor(StreamOkHttpInterceptors.connectionId(connectionIdHolder))
                addInterceptor(
                    StreamOkHttpInterceptors.auth("jwt", tokenManager, compositeSerialization)
                )
                addInterceptor(StreamOkHttpInterceptors.error(compositeSerialization))
            }
        }
        configuredInterceptors.forEach { httpBuilder.addInterceptor(it) }
    }

    return StreamClientImpl(
        userId = userId,
        scope = clientScope,
        tokenManager = tokenManager,
        singleFlight = singleFlight,
        serialQueue = serialQueue,
        connectionIdHolder = connectionIdHolder,
        logger = clientLogger,
        mutableConnectionState = MutableStateFlow(StreamConnectionState.Idle),
        subscriptionManager = clientSubscriptionManager,
        networkMonitor = networkMonitor,
        socketSession =
            StreamSocketSession(
                logger = logProvider.taggedLogger("SCSocketSession"),
                products = products,
                config =
                    StreamSocketConfig.jwt(
                        url = wsUrl.rawValue,
                        apiKey = apiKey,
                        clientInfoHeader = clientInfoHeader,
                    ),
                jsonSerialization = compositeSerialization,
                eventParser =
                    StreamCompositeEventSerializationImpl(
                        internal =
                            serializationConfig.eventParser
                                ?: StreamEventSerialization(compositeSerialization),
                        external = serializationConfig.productEventSerializers,
                    ),
                healthMonitor = healthMonitor,
                batcher = batcher,
                internalSocket = socket,
                subscriptionManager =
                    StreamSubscriptionManager(
                        logger = logProvider.taggedLogger("SCSocketSessionSubscriptions")
                    ),
            ),
    )
}
