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
package io.getstream.android.core.internal.client

import io.getstream.android.core.api.StreamClient
import io.getstream.android.core.api.authentication.StreamTokenManager
import io.getstream.android.core.api.authentication.StreamTokenProvider
import io.getstream.android.core.api.http.StreamOkHttpInterceptors
import io.getstream.android.core.api.log.StreamLoggerProvider
import io.getstream.android.core.api.model.config.StreamEndpointConfig
import io.getstream.android.core.api.model.config.StreamSocketConfig
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
import io.getstream.android.core.api.state.StreamClientState
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager
import io.getstream.android.core.internal.authentication.StreamTokenManagerImpl
import io.getstream.android.core.internal.processing.StreamDebounceProcessorImpl
import io.getstream.android.core.internal.processing.StreamRetryProcessorImpl
import io.getstream.android.core.internal.processing.StreamSerialProcessingQueueImpl
import io.getstream.android.core.internal.processing.StreamSingleFlightProcessorImpl
import io.getstream.android.core.internal.serialization.StreamMoshiJsonSerializationImpl
import io.getstream.android.core.internal.serialization.moshi.MoshiProvider
import io.getstream.android.core.internal.socket.StreamClientEventsParser
import io.getstream.android.core.internal.socket.StreamSocketSession
import io.getstream.android.core.internal.socket.StreamWebSocketImpl
import io.getstream.android.core.internal.socket.connection.StreamConnectionIdHolderImpl
import io.getstream.android.core.internal.socket.factory.StreamWebSocketFactoryImpl
import io.getstream.android.core.internal.socket.monitor.StreamHealthMonitorImpl
import io.getstream.android.core.internal.state.StreamClientStateImpl
import io.getstream.android.core.internal.subscribe.StreamSubscriptionManagerImpl
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient

@OptIn(ExperimentalTime::class)
internal fun createClient(
    // Client config
    apiKey: StreamApiKey,
    userId: StreamUserId,
    endpoint: StreamEndpointConfig,
    clientSubscriptionManager: StreamSubscriptionManager<StreamClientListener>? = null,
    // Token
    tokenProvider: StreamTokenProvider,
    tokenManager: StreamTokenManager? = null,
    // Processing
    singleFlightProcessor: StreamSingleFlightProcessor? = null,
    serialProcessingQueue: StreamSerialProcessingQueue? = null,
    retryProcessor: StreamRetryProcessor? = null,
    // Http
    httpInterceptors: List<Interceptor> = emptyList(),
    okHttpClient: OkHttpClient? = null,
    // Socket
    connectionIdHolder: StreamConnectionIdHolder? = null,
    socketFactory: StreamWebSocketFactory? = null,
    healthMonitor: StreamHealthMonitor? = null,
    socketSubscriptionManager: StreamSubscriptionManager<StreamWebSocketListener>? = null,
    socketEventDebounceProcessor: StreamDebounceMessageProcessor<String>? = null,
    // Misc
    json: StreamJsonSerialization? = null,
    logProvider: StreamLoggerProvider = StreamLoggerProvider.defaultAndroidLogger(),
    scope: CoroutineScope,
): StreamClient<StreamClientState> {
    val clientLogger = logProvider.taggedLogger(tag = "StreamCoreClient")
    val parent = scope.coroutineContext[Job]
    val supervisorJob = if (parent != null) {
        SupervisorJob(parent)
    } else {
        SupervisorJob()
    }
    val clientScope = CoroutineScope(supervisorJob + scope.coroutineContext)
    val state = StreamClientStateImpl()
    clientLogger.d { "Creating StreamClient" }

    // Processors
    val serialQueue =
        serialProcessingQueue
            ?: run {
                clientLogger.d { "Using default StreamSerialProcessingQueue" }
                StreamSerialProcessingQueueImpl(
                    logProvider.taggedLogger("StreamCoreSerialQueue"),
                    clientScope,
                )
            }

    val retryProcessor =
        retryProcessor
            ?: run {
                clientLogger.d { "Using default StreamRetryProcessor" }
                StreamRetryProcessorImpl()
            }

    val singleFlight =
        singleFlightProcessor
            ?: run {
                clientLogger.d { "Using default StreamSingleFlightProcessor" }
                StreamSingleFlightProcessorImpl(scope)
            }

    val connectionIdHolder: StreamConnectionIdHolder =
        connectionIdHolder
            ?: run {
                clientLogger.d { "Using default StreamConnectionIdHolder" }
                StreamConnectionIdHolderImpl()
            }

    val tokenManager =
        tokenManager
            ?: run {
                clientLogger.d { "Using default StreamTokenManager" }
                StreamTokenManagerImpl(userId, tokenProvider, singleFlight)
            }

    val okHttpInterceptors =
        httpInterceptors +
            run {
                clientLogger.d {
                    "Adding connectionId interceptor from StreamOkHttpInterceptors.connectionId()"
                }
                StreamOkHttpInterceptors.connectionId(connectionIdHolder)
            }

    val socketConfig =
        StreamSocketConfig.jwt(
            url = endpoint.wsUrl,
            apiKey = apiKey,
            clientInfoHeader = endpoint.clientInfoHeader,
        )

    val socket =
        StreamWebSocketImpl(
            logger = logProvider.taggedLogger("StreamCoreSocket"),
            socketFactory =
                socketFactory
                    ?: StreamWebSocketFactoryImpl(
                        logger = logProvider.taggedLogger("StreamCoreSocketFactory")
                    ),
            subscriptionManager = socketSubscriptionManager ?: StreamSubscriptionManagerImpl(),
        )

    val jsonSerialization =
        json ?: StreamMoshiJsonSerializationImpl(MoshiProvider().builder {}.build())

    supervisorJob.invokeOnCompletion {
        clientLogger.d { "StreamClient scope completed. Cancelling resources" }
        clientScope.cancel()
        runBlocking { serialQueue.stop() }
        singleFlight.clear(true)
        runBlocking { socket.close() }
        runBlocking { healthMonitor?.stop() }
        runBlocking { socketEventDebounceProcessor?.stop() }
        runBlocking { serialQueue.stop() }
    }

    return StreamClientIImpl(
        userId = userId,
        tokenManager = tokenManager,
        singleFlight = singleFlight,
        serialQueue = serialQueue,
        connectionIdHolder = connectionIdHolder,
        logger = clientLogger,
        mutableClientState = state,
        subscriptionManager = clientSubscriptionManager ?: StreamSubscriptionManagerImpl(),
        socketSession =
            StreamSocketSession(
                logger = logProvider.taggedLogger("StreamCoreSocketSession"),
                config = socketConfig,
                jsonSerialization = jsonSerialization,
                eventParser = StreamClientEventsParser(jsonSerialization),
                healthMonitor = healthMonitor ?: StreamHealthMonitorImpl(clientLogger, clientScope),
                debounceProcessor =
                    socketEventDebounceProcessor ?: StreamDebounceProcessorImpl(clientScope),
                internalSocket = socket,
                subscriptionManager = clientSubscriptionManager ?: StreamSubscriptionManagerImpl(),
            ),
    )
}
