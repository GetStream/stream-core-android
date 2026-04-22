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

package io.getstream.android.core.api

import android.annotation.SuppressLint
import android.content.Context
import io.getstream.android.core.annotations.StreamInternalApi
import io.getstream.android.core.api.authentication.StreamTokenManager
import io.getstream.android.core.api.authentication.StreamTokenProvider
import io.getstream.android.core.api.components.StreamAndroidComponentsProvider
import io.getstream.android.core.api.http.StreamOkHttpInterceptors
import io.getstream.android.core.api.log.StreamLoggerProvider
import io.getstream.android.core.api.model.StreamUser
import io.getstream.android.core.api.model.config.StreamClientSerializationConfig
import io.getstream.android.core.api.model.config.StreamComponentProvider
import io.getstream.android.core.api.model.config.StreamHttpConfig
import io.getstream.android.core.api.model.config.StreamSocketConfig
import io.getstream.android.core.api.model.connection.StreamConnectedUser
import io.getstream.android.core.api.model.connection.StreamConnectionState
import io.getstream.android.core.api.model.connection.lifecycle.StreamLifecycleState
import io.getstream.android.core.api.model.connection.network.StreamNetworkState
import io.getstream.android.core.api.observers.lifecycle.StreamLifecycleMonitor
import io.getstream.android.core.api.observers.network.StreamNetworkMonitor
import io.getstream.android.core.api.processing.StreamEventAggregator
import io.getstream.android.core.api.processing.StreamSerialProcessingQueue
import io.getstream.android.core.api.processing.StreamSingleFlightProcessor
import io.getstream.android.core.api.recovery.StreamConnectionRecoveryEvaluator
import io.getstream.android.core.api.serialization.StreamEventSerialization
import io.getstream.android.core.api.socket.StreamConnectionIdHolder
import io.getstream.android.core.api.socket.StreamWebSocket
import io.getstream.android.core.api.socket.StreamWebSocketFactory
import io.getstream.android.core.api.socket.listeners.StreamClientListener
import io.getstream.android.core.api.socket.monitor.StreamHealthMonitor
import io.getstream.android.core.api.subscribe.StreamObservable
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager
import io.getstream.android.core.internal.client.StreamClientImpl
import io.getstream.android.core.internal.observers.StreamNetworkAndLifeCycleMonitor
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
public interface StreamClient : StreamObservable<StreamClientListener> {
    /**
     * Read-only, hot state holder for this client.
     *
     * **Semantics**
     * - Emits connection status changes (e.g., connecting/connected/disconnected).
     * - Hot & conflated: new collectors receive the latest value immediately.
     */
    public val connectionState: StateFlow<StreamConnectionState>

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
}

/**
 * Creates a [StreamClient] with mandatory identity parameters and optional configuration.
 *
 * This is the primary entry point for product SDKs to create a client. All internal components are
 * created with sensible defaults. Use [components] to replace specific internal components (for
 * sharing instances or custom implementations).
 *
 * ### Usage
 *
 * ```kotlin
 * // Minimal — all defaults
 * val client = StreamClient(
 *     scope = scope,
 *     context = context,
 *     user = user,
 *     tokenProvider = tokenProvider,
 *     products = listOf("chat"),
 *     socketConfig = StreamSocketConfig.jwt(
 *         url = StreamWsUrl.fromString("wss://chat.stream-io-api.com/connect"),
 *         apiKey = apiKey,
 *         clientInfoHeader = clientInfoHeader,
 *     ),
 *     serializationConfig = StreamClientSerializationConfig.default(chatEventSerializer),
 * )
 *
 * // With tuned socket, custom logging, HTTP, and component overrides
 * val singleFlight = StreamSingleFlightProcessor(scope)
 * val client = StreamClient(
 *     scope = scope,
 *     context = context,
 *     user = user,
 *     tokenProvider = tokenProvider,
 *     products = listOf("feeds"),
 *     socketConfig = StreamSocketConfig.jwt(
 *         url = StreamWsUrl.fromString("wss://feeds.stream-io-api.com/connect"),
 *         apiKey = apiKey,
 *         clientInfoHeader = clientInfoHeader,
 *         healthCheckIntervalMs = 30_000,
 *     ),
 *     serializationConfig = StreamClientSerializationConfig.default(feedsEventSerializer),
 *     httpConfig = StreamHttpConfig(httpBuilder),
 *     components = StreamComponentProvider(
 *         logProvider = myLogProvider,
 *         singleFlight = singleFlight,
 *     ),
 * )
 * ```
 *
 * @param scope Coroutine scope powering internal work. Recommended:
 *   `CoroutineScope(SupervisorJob() + Dispatchers.Default)`.
 * @param context Android application context.
 * @param user User identity.
 * @param tokenProvider Provides authentication tokens on demand.
 * @param products Stream product codes negotiated with the socket (e.g. "chat", "feeds", "video").
 * @param socketConfig WebSocket connection configuration (URL, auth, timing, batching).
 * @param serializationConfig JSON and event serialization configuration.
 * @param httpConfig Optional HTTP client customization (OkHttp builder, interceptors).
 * @param components Optional component overrides for DI. Defaults to
 *   [StreamComponentProvider()][StreamComponentProvider] (all defaults).
 */
@Suppress("LongParameterList", "CyclomaticComplexMethod")
@SuppressLint("ExposeAsStateFlow")
@StreamInternalApi
public fun StreamClient(
    scope: CoroutineScope,
    context: Context,
    user: StreamUser,
    tokenProvider: StreamTokenProvider,
    products: List<String>,
    socketConfig: StreamSocketConfig,
    serializationConfig: StreamClientSerializationConfig,
    httpConfig: StreamHttpConfig? = null,
    components: StreamComponentProvider = StreamComponentProvider(),
): StreamClient {
    val logProvider = components.logProvider
    val singleFlight = components.singleFlight ?: StreamSingleFlightProcessor(scope)

    return createStreamClientInternal(
        scope = scope,
        context = context,
        user = user,
        tokenProvider = tokenProvider,
        products = products,
        socketConfig = socketConfig,
        serializationConfig = serializationConfig,
        httpConfig = httpConfig,
        androidComponentsProvider =
            components.androidComponentsProvider
                ?: StreamAndroidComponentsProvider(context.applicationContext),
        logProvider = logProvider,
        clientSubscriptionManager =
            components.clientSubscriptionManager
                ?: StreamSubscriptionManager(
                    logger = logProvider.taggedLogger("SCClientSubscriptions"),
                    maxStrongSubscriptions = 250,
                    maxWeakSubscriptions = 250,
                ),
        singleFlight = singleFlight,
        serialQueue =
            components.serialQueue
                ?: StreamSerialProcessingQueue(
                    logger = logProvider.taggedLogger("SCSerialProcessing"),
                    scope = scope,
                ),
        tokenManager =
            components.tokenManager ?: StreamTokenManager(user.id, tokenProvider, singleFlight),
        connectionIdHolder = components.connectionIdHolder ?: StreamConnectionIdHolder(),
        socketFactory =
            components.socketFactory
                ?: StreamWebSocketFactory(logger = logProvider.taggedLogger("SCWebSocketFactory")),
        eventAggregator = components.eventAggregator,
        healthMonitor =
            components.healthMonitor
                ?: StreamHealthMonitor(
                    logger = logProvider.taggedLogger("SCHealthMonitor"),
                    scope = scope,
                    interval = socketConfig.healthCheckIntervalMs,
                    livenessThreshold = socketConfig.livenessThresholdMs,
                ),
        networkMonitor = components.networkMonitor,
        lifecycleMonitor = components.lifecycleMonitor,
        connectionRecoveryEvaluator = components.connectionRecoveryEvaluator,
    )
}

/**
 * Internal full-parameter factory. Used by the simplified [StreamClient] factory above and
 * available for tests requiring full DI control.
 */
@Suppress("LongParameterList", "LongMethod")
@SuppressLint("ExposeAsStateFlow")
internal fun createStreamClientInternal(

    // Android
    scope: CoroutineScope,
    context: Context,

    // Client config
    user: StreamUser,
    tokenProvider: StreamTokenProvider,
    products: List<String>,
    socketConfig: StreamSocketConfig,
    serializationConfig: StreamClientSerializationConfig,
    httpConfig: StreamHttpConfig? = null,

    // Component provider
    androidComponentsProvider: StreamAndroidComponentsProvider =
        StreamAndroidComponentsProvider(context.applicationContext),

    // Logging
    logProvider: StreamLoggerProvider = StreamLoggerProvider.defaultAndroidLogger(),

    // Subscriptions
    clientSubscriptionManager: StreamSubscriptionManager<StreamClientListener> =
        StreamSubscriptionManager(
            logger = logProvider.taggedLogger("SCClientSubscriptions"),
            maxStrongSubscriptions = 250,
            maxWeakSubscriptions = 250,
        ),

    // Processing
    singleFlight: StreamSingleFlightProcessor = StreamSingleFlightProcessor(scope),
    serialQueue: StreamSerialProcessingQueue =
        StreamSerialProcessingQueue(
            logger = logProvider.taggedLogger("SCSerialProcessing"),
            scope = scope,
        ),
    // Token
    tokenManager: StreamTokenManager = StreamTokenManager(user.id, tokenProvider, singleFlight),

    // Socket
    connectionIdHolder: StreamConnectionIdHolder = StreamConnectionIdHolder(),
    socketFactory: StreamWebSocketFactory =
        StreamWebSocketFactory(logger = logProvider.taggedLogger("SCWebSocketFactory")),
    eventAggregator: StreamEventAggregator<*>? = null,

    // Monitoring
    healthMonitor: StreamHealthMonitor =
        StreamHealthMonitor(
            logger = logProvider.taggedLogger("SCHealthMonitor"),
            scope = scope,
            interval = socketConfig.healthCheckIntervalMs,
            livenessThreshold = socketConfig.livenessThresholdMs,
        ),
    networkMonitor: StreamNetworkMonitor? = null,
    lifecycleMonitor: StreamLifecycleMonitor? = null,
    connectionRecoveryEvaluator: StreamConnectionRecoveryEvaluator? = null,
): StreamClient {
    val resolvedNetworkMonitor =
        networkMonitor
            ?: StreamNetworkMonitor(
                logger = logProvider.taggedLogger("SCNetworkMonitor"),
                scope = scope,
                connectivityManager = androidComponentsProvider.connectivityManager().getOrThrow(),
                wifiManager = androidComponentsProvider.wifiManager().getOrThrow(),
                telephonyManager = androidComponentsProvider.telephonyManager().getOrThrow(),
                subscriptionManager =
                    StreamSubscriptionManager(
                        logger = logProvider.taggedLogger("SCNetworkMonitorSubscriptions")
                    ),
            )
    val resolvedLifecycleMonitor =
        lifecycleMonitor
            ?: StreamLifecycleMonitor(
                logger = logProvider.taggedLogger("SCLifecycleMonitor"),
                subscriptionManager =
                    StreamSubscriptionManager(
                        logger = logProvider.taggedLogger("SCLifecycleMonitorSubscriptions")
                    ),
                lifecycle = androidComponentsProvider.lifecycle(),
            )
    val resolvedRecoveryEvaluator =
        connectionRecoveryEvaluator
            ?: StreamConnectionRecoveryEvaluator(
                logger = logProvider.taggedLogger("SCConnectionRecoveryEvaluator"),
                singleFlightProcessor = singleFlight,
            )
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
                addInterceptor(StreamOkHttpInterceptors.clientInfo(socketConfig.clientInfoHeader))
                addInterceptor(StreamOkHttpInterceptors.apiKey(socketConfig.apiKey))
                addInterceptor(StreamOkHttpInterceptors.connectionId(connectionIdHolder))
                addInterceptor(
                    StreamOkHttpInterceptors.auth(
                        socketConfig.authType,
                        tokenManager,
                        compositeSerialization,
                    )
                )
                addInterceptor(StreamOkHttpInterceptors.error(compositeSerialization))
            }
        }
        configuredInterceptors.forEach { httpBuilder.addInterceptor(it) }
    }

    val networkAndLifeCycleMonitor =
        StreamNetworkAndLifeCycleMonitor(
            logger = logProvider.taggedLogger("SCNetworkAndLifecycleMonitor"),
            networkMonitor = resolvedNetworkMonitor,
            lifecycleMonitor = resolvedLifecycleMonitor,
            mutableNetworkState = MutableStateFlow(StreamNetworkState.Unknown),
            mutableLifecycleState = MutableStateFlow(StreamLifecycleState.Unknown),
            subscriptionManager =
                StreamSubscriptionManager(
                    logger = logProvider.taggedLogger("SCNLMonitorSubscriptions")
                ),
        )

    val eventParser =
        StreamCompositeEventSerializationImpl(
            internal =
                serializationConfig.eventParser
                    ?: StreamEventSerialization(compositeSerialization),
            external = serializationConfig.productEventSerializers,
        )

    val resolvedAggregator =
        eventAggregator
            ?: StreamEventAggregator(
                scope = clientScope,
                typeExtractor = { raw -> eventParser.peekType(raw) },
                deserializer = { raw -> eventParser.deserialize(raw) },
                aggregationThreshold = socketConfig.aggregationThreshold,
                maxWindowMs = socketConfig.aggregationMaxWindowMs,
                dispatchQueueCapacity = socketConfig.aggregationDispatchQueueCapacity,
            )

    val mutableConnectionState = MutableStateFlow<StreamConnectionState>(StreamConnectionState.Idle)
    return StreamClientImpl(
        user = user,
        scope = clientScope,
        tokenManager = tokenManager,
        singleFlight = singleFlight,
        serialQueue = serialQueue,
        connectionIdHolder = connectionIdHolder,
        logger = clientLogger,
        mutableConnectionState = mutableConnectionState,
        subscriptionManager = clientSubscriptionManager,
        networkAndLifeCycleMonitor = networkAndLifeCycleMonitor,
        connectionRecoveryEvaluator = resolvedRecoveryEvaluator,
        socketSession =
            StreamSocketSession(
                logger = logProvider.taggedLogger("SCSocketSession"),
                products = products,
                config = socketConfig,
                jsonSerialization = compositeSerialization,
                eventParser = eventParser,
                healthMonitor = healthMonitor,
                aggregator = resolvedAggregator,
                internalSocket = socket,
                subscriptionManager =
                    StreamSubscriptionManager(
                        logger = logProvider.taggedLogger("SCSocketSessionSubscriptions")
                    ),
            ),
    )
}
