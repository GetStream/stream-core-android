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
package io.getstream.android.core.sample.client

import android.content.Context
import io.getstream.android.core.api.StreamClient
import io.getstream.android.core.api.authentication.StreamTokenManager
import io.getstream.android.core.api.authentication.StreamTokenProvider
import io.getstream.android.core.api.components.StreamAndroidComponentsProvider
import io.getstream.android.core.api.log.StreamLogger
import io.getstream.android.core.api.log.StreamLoggerProvider
import io.getstream.android.core.api.model.config.StreamClientSerializationConfig
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
import io.getstream.android.core.api.socket.StreamWebSocketFactory
import io.getstream.android.core.api.socket.listeners.StreamClientListener
import io.getstream.android.core.api.socket.monitor.StreamHealthMonitor
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager
import kotlinx.coroutines.CoroutineScope

/**
 * Creates a [createStreamClient] instance with the given configuration and dependencies.
 *
 * @param scope The coroutine scope.
 * @param apiKey The API key.
 * @param userId The user ID.
 * @param wsUrl The WebSocket URL.
 * @param clientInfoHeader The client info header.
 * @param tokenProvider The token provider.
 * @return A new [createStreamClient] instance.
 */
fun createStreamClient(
    context: Context,
    scope: CoroutineScope,
    apiKey: StreamApiKey,
    userId: StreamUserId,
    wsUrl: StreamWsUrl,
    clientInfoHeader: StreamHttpClientInfoHeader,
    tokenProvider: StreamTokenProvider,
): StreamClient {
    val logProvider =
        StreamLoggerProvider.Companion.defaultAndroidLogger(
            minLevel = StreamLogger.LogLevel.Verbose,
            honorAndroidIsLoggable = false,
        )
    val clientSubscriptionManager =
        StreamSubscriptionManager<StreamClientListener>(
            logger = logProvider.taggedLogger("SCClientSubscriptions"),
            maxStrongSubscriptions = 250,
            maxWeakSubscriptions = 250,
        )
    val singleFlight = StreamSingleFlightProcessor(scope)
    val tokenManager = StreamTokenManager(userId, tokenProvider, singleFlight)
    val serialQueue =
        StreamSerialProcessingQueue(
            logger = logProvider.taggedLogger("SCSerialProcessing"),
            scope = scope,
        )
    val retryProcessor = StreamRetryProcessor(logger = logProvider.taggedLogger("SCRetryProcessor"))
    val connectionIdHolder = StreamConnectionIdHolder()
    val socketFactory =
        StreamWebSocketFactory(logger = logProvider.taggedLogger("SCWebSocketFactory"))
    val healthMonitor =
        StreamHealthMonitor(logger = logProvider.taggedLogger("SCHealthMonitor"), scope = scope)
    val batcher =
        StreamBatcher<String>(
            scope = scope,
            batchSize = 10,
            initialDelayMs = 100L,
            maxDelayMs = 1_000L,
        )

    val androidComponentsProvider = StreamAndroidComponentsProvider(context)
    val connectivityManager = androidComponentsProvider.connectivityManager().getOrThrow()
    val wifiManager = androidComponentsProvider.wifiManager().getOrThrow()
    val telephonyManager = androidComponentsProvider.telephonyManager().getOrThrow()
    val networkMonitor =
        StreamNetworkMonitor(
            logger = logProvider.taggedLogger("SCNetworkMonitor"),
            scope = scope,
            connectivityManager = connectivityManager,
            wifiManager = wifiManager,
            telephonyManager = telephonyManager,
            subscriptionManager =
                StreamSubscriptionManager(
                    logger = logProvider.taggedLogger("SCNetworkMonitorSubscriptions")
                ),
        )

    return StreamClient(
        scope = scope,
        apiKey = apiKey,
        userId = userId,
        wsUrl = wsUrl,
        products = listOf("feeds"),
        clientInfoHeader = clientInfoHeader,
        tokenProvider = tokenProvider,
        logProvider = logProvider,
        clientSubscriptionManager = clientSubscriptionManager,
        tokenManager = tokenManager,
        singleFlight = singleFlight,
        serialQueue = serialQueue,
        retryProcessor = retryProcessor,
        connectionIdHolder = connectionIdHolder,
        socketFactory = socketFactory,
        healthMonitor = healthMonitor,
        networkMonitor = networkMonitor,
        serializationConfig =
            StreamClientSerializationConfig.default(
                object : StreamEventSerialization<Unit> {
                    override fun serialize(data: Unit): Result<String> = Result.success("")

                    override fun deserialize(raw: String): Result<Unit> = Result.success(Unit)
                }
            ),
        batcher = batcher
    )
}
