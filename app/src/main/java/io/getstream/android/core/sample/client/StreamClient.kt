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
package io.getstream.android.core.sample.client

import io.getstream.android.core.api.StreamClient
import io.getstream.android.core.api.authentication.StreamTokenManager
import io.getstream.android.core.api.authentication.StreamTokenProvider
import io.getstream.android.core.api.log.StreamLogger
import io.getstream.android.core.api.log.StreamLoggerProvider
import io.getstream.android.core.api.model.value.StreamApiKey
import io.getstream.android.core.api.model.value.StreamHttpClientInfoHeader
import io.getstream.android.core.api.model.value.StreamUserId
import io.getstream.android.core.api.model.value.StreamWsUrl
import io.getstream.android.core.api.processing.StreamBatcher
import io.getstream.android.core.api.processing.StreamRetryProcessor
import io.getstream.android.core.api.processing.StreamSerialProcessingQueue
import io.getstream.android.core.api.processing.StreamSingleFlightProcessor
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
 * @param module The client module.
 * @return A new [createStreamClient] instance.
 */
fun createStreamClient(
    scope: CoroutineScope,
    apiKey: StreamApiKey,
    userId: StreamUserId,
    wsUrl: StreamWsUrl,
    clientInfoHeader: StreamHttpClientInfoHeader,
    tokenProvider: StreamTokenProvider,
    module: StreamClientModule = StreamClientModule.defaults(scope, userId, tokenProvider),
): StreamClient =
    StreamClient(
        scope = scope,
        apiKey = apiKey,
        userId = userId,
        wsUrl = wsUrl,
        products = listOf("feeds"),
        clientInfoHeader = clientInfoHeader,
        tokenProvider = tokenProvider,
        logProvider = module.logProvider,
        clientSubscriptionManager = module.clientSubscriptionManager,
        tokenManager = module.tokenManager,
        singleFlight = module.singleFlight,
        serialQueue = module.serialQueue,
        retryProcessor = module.retryProcessor,
        connectionIdHolder = module.connectionIdHolder,
        socketFactory = module.socketFactory,
        healthMonitor = module.healthMonitor,
        batcher = module.batcher,
    )

/**
 * Holds configuration and dependencies for the Stream client, including logging, coroutine scope,
 * subscription managers, token management, processing queues, retry logic, connection handling,
 * socket factory, health monitoring, event debouncing, and JSON serialization.
 *
 * This module is intended to be used as a central place for client-wide resources and processors.
 *
 * @property logProvider Provides logging functionality for the client.
 * @property scope Coroutine scope used for async operations.
 * @property clientSubscriptionManager Manages subscriptions for client listeners.
 * @property tokenManager Handles authentication tokens.
 * @property singleFlight Ensures single execution of concurrent requests.
 * @property serialQueue Serializes processing of tasks.
 * @property retryProcessor Handles retry logic for failed operations.
 * @property connectionIdHolder Stores the current connection ID.
 * @property socketFactory Creates WebSocket connections.
 * @property healthMonitor Monitors connection health.
 * @property batcher Batches socket events for batch processing.
 */
@ConsistentCopyVisibility
data class StreamClientModule
private constructor(
    val logProvider: StreamLoggerProvider =
        StreamLoggerProvider.Companion.defaultAndroidLogger(
            minLevel = StreamLogger.LogLevel.Verbose,
            honorAndroidIsLoggable = false,
        ),
    val scope: CoroutineScope,
    val clientSubscriptionManager: StreamSubscriptionManager<StreamClientListener> =
        StreamSubscriptionManager(
            logger = logProvider.taggedLogger("SCClientSubscriptions"),
            maxStrongSubscriptions = 250,
            maxWeakSubscriptions = 250,
        ),
    val tokenManager: StreamTokenManager,
    val singleFlight: StreamSingleFlightProcessor = StreamSingleFlightProcessor(scope = scope),
    val serialQueue: StreamSerialProcessingQueue =
        StreamSerialProcessingQueue(
            logger = logProvider.taggedLogger("SCSerialProcessing"),
            scope = scope,
        ),
    val retryProcessor: StreamRetryProcessor =
        StreamRetryProcessor(logger = logProvider.taggedLogger("SCRetryProcessor")),
    val connectionIdHolder: StreamConnectionIdHolder = StreamConnectionIdHolder(),
    val socketFactory: StreamWebSocketFactory =
        StreamWebSocketFactory(logger = logProvider.taggedLogger("SCWebSocketFactory")),
    val healthMonitor: StreamHealthMonitor =
        StreamHealthMonitor(logger = logProvider.taggedLogger("SCHealthMonitor"), scope = scope),
    val batcher: StreamBatcher<String> =
        StreamBatcher(scope = scope, batchSize = 10, initialDelayMs = 100L, maxDelayMs = 1_000L),
) {
    companion object {
        /**
         * Creates a default [StreamClientModule] instance with recommended settings and
         * dependencies.
         *
         * @param scope Coroutine scope for async operations.
         * @param userId The user ID for authentication.
         * @param tokenProvider Provider for authentication tokens.
         * @return A configured [StreamClientModule] instance.
         */
        fun defaults(
            scope: CoroutineScope,
            userId: StreamUserId,
            tokenProvider: StreamTokenProvider,
        ): StreamClientModule {
            val singleFlight = StreamSingleFlightProcessor(scope)
            return StreamClientModule(
                scope = scope,
                singleFlight = singleFlight,
                tokenManager = StreamTokenManager(userId, tokenProvider, singleFlight),
            )
        }
    }
}
