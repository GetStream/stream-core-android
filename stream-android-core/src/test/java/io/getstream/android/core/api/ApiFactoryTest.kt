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
@file:OptIn(StreamInternalApi::class)

package io.getstream.android.core.api

import io.getstream.android.core.annotations.StreamInternalApi
import io.getstream.android.core.api.authentication.StreamTokenManager as StreamTokenManagerFactory
import io.getstream.android.core.api.authentication.StreamTokenProvider
import io.getstream.android.core.api.log.StreamLogger
import io.getstream.android.core.api.model.value.StreamUserId
import io.getstream.android.core.api.processing.StreamBatcher as StreamBatcherFactory
import io.getstream.android.core.api.processing.StreamRetryProcessor as StreamRetryProcessorFactory
import io.getstream.android.core.api.processing.StreamSerialProcessingQueue as StreamSerialProcessingQueueFactory
import io.getstream.android.core.api.processing.StreamSingleFlightProcessor as StreamSingleFlightProcessorFactory
import io.getstream.android.core.api.serialization.StreamEventSerialization as StreamEventSerializationFactory
import io.getstream.android.core.api.serialization.StreamJsonSerialization
import io.getstream.android.core.api.socket.StreamConnectionIdHolder as StreamConnectionIdHolderFactory
import io.getstream.android.core.api.socket.StreamWebSocket as StreamWebSocketFactoryMethod
import io.getstream.android.core.api.socket.StreamWebSocketFactory as StreamWebSocketFactoryFactory
import io.getstream.android.core.api.socket.StreamWebSocketFactory
import io.getstream.android.core.api.socket.listeners.StreamWebSocketListener
import io.getstream.android.core.api.socket.monitor.StreamHealthMonitor as StreamHealthMonitorFactory
import io.getstream.android.core.api.subscribe.StreamSubscription
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager as StreamSubscriptionManagerFactory
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager
import io.getstream.android.core.internal.authentication.StreamTokenManagerImpl
import io.getstream.android.core.internal.processing.StreamBatcherImpl
import io.getstream.android.core.internal.processing.StreamRetryProcessorImpl
import io.getstream.android.core.internal.processing.StreamSerialProcessingQueueImpl
import io.getstream.android.core.internal.processing.StreamSingleFlightProcessorImpl
import io.getstream.android.core.internal.serialization.StreamClientEventSerializationImpl
import io.getstream.android.core.internal.socket.StreamWebSocketImpl
import io.getstream.android.core.internal.socket.connection.StreamConnectionIdHolderImpl
import io.getstream.android.core.internal.socket.factory.StreamWebSocketFactoryImpl
import io.getstream.android.core.internal.socket.monitor.StreamHealthMonitorImpl
import io.getstream.android.core.internal.subscribe.StreamSubscriptionManagerImpl
import io.getstream.android.core.testutil.assertFieldEquals
import io.getstream.android.core.testutil.readPrivateField
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import okhttp3.OkHttpClient
import org.junit.Test

internal class ApiFactoryTest {

    private val dispatcher = StandardTestDispatcher()
    private val testScope = TestScope(dispatcher)

    @Test
    fun `StreamSerialProcessingQueue factory wires implementation`() {
        // Given
        val logger = mockk<StreamLogger>(relaxed = true)
        val autoStart = false
        val startMode = CoroutineStart.LAZY
        val capacity = 7

        // When
        val queue =
            StreamSerialProcessingQueueFactory(
                logger = logger,
                scope = testScope,
                autoStart = autoStart,
                startMode = startMode,
                capacity = capacity,
            )

        // Then
        assertTrue(queue is StreamSerialProcessingQueueImpl)
        queue.assertFieldEquals("logger", logger)
        queue.assertFieldEquals("scope", testScope)
        queue.assertFieldEquals("autoStart", autoStart)
        queue.assertFieldEquals("startMode", startMode)
        val inbox = queue.readPrivateField("inbox")!!
        val capacityValue = inbox.readPrivateField("capacity")
        assertEquals(capacity, capacityValue)
    }

    @Test
    fun `StreamSingleFlightProcessor factory uses implementation and scope`() {
        // Given
        val scope = TestScope(dispatcher)

        // When
        val processor = StreamSingleFlightProcessorFactory(scope = scope)

        // Then
        assertTrue(processor is StreamSingleFlightProcessorImpl)
        processor.assertFieldEquals("scope", scope)
    }

    @Test
    fun `StreamBatcher factory wires configuration`() {
        // Given
        val scope = TestScope(dispatcher)
        val batchSize = 5
        val initialDelay = 50L
        val maxDelay = 500L
        val autoStart = false
        val capacity = 3

        // When
        val batcher =
            StreamBatcherFactory<Int>(
                scope = scope,
                batchSize = batchSize,
                initialDelayMs = initialDelay,
                maxDelayMs = maxDelay,
                autoStart = autoStart,
                channelCapacity = capacity,
            )

        // Then
        assertTrue(batcher is StreamBatcherImpl)
        batcher.assertFieldEquals("scope", scope)
        batcher.assertFieldEquals("batchSize", batchSize)
        batcher.assertFieldEquals("initialDelayMs", initialDelay)
        batcher.assertFieldEquals("maxDelayMs", maxDelay)
        batcher.assertFieldEquals("autoStart", autoStart)
        val inbox = batcher.readPrivateField("inbox")!!
        val capacityValue = inbox.readPrivateField("capacity")
        assertEquals(capacity, capacityValue)
    }

    @Test
    fun `StreamRetryProcessor factory passes logger`() {
        // Given
        val logger = mockk<StreamLogger>(relaxed = true)

        // When
        val retryProcessor = StreamRetryProcessorFactory(logger)

        // Then
        assertTrue(retryProcessor is StreamRetryProcessorImpl)
        retryProcessor.assertFieldEquals("logger", logger)
    }

    @Test
    fun `StreamEventSerialization factory passes json parser`() {
        // Given
        val jsonParser = mockk<StreamJsonSerialization>(relaxed = true)

        // When
        val serialization = StreamEventSerializationFactory(jsonParser)

        // Then
        assertTrue(serialization is StreamClientEventSerializationImpl)
        serialization.assertFieldEquals("jsonParser", jsonParser)
    }

    @Test
    fun `StreamWebSocket factory wires dependencies`() {
        // Given
        val logger = mockk<StreamLogger>(relaxed = true)
        val socketFactory = mockk<StreamWebSocketFactory>(relaxed = true)
        val subscriptionManager =
            mockk<StreamSubscriptionManager<StreamWebSocketListener>>(relaxed = true)

        // When
        val webSocket =
            StreamWebSocketFactoryMethod(
                logger = logger,
                socketFactory = socketFactory,
                subscriptionManager = subscriptionManager,
            )

        // Then
        assertTrue(webSocket is StreamWebSocketImpl<*>)
        webSocket.assertFieldEquals("logger", logger)
        webSocket.assertFieldEquals("socketFactory", socketFactory)
        webSocket.assertFieldEquals("subscriptionManager", subscriptionManager)
    }

    @Test
    fun `StreamWebSocketFactory factory wires okHttp and logger`() {
        // Given
        val okHttpClient = OkHttpClient()
        val logger = mockk<StreamLogger>(relaxed = true)

        // When
        val factory = StreamWebSocketFactoryFactory(okHttpClient = okHttpClient, logger = logger)

        // Then
        assertTrue(factory is StreamWebSocketFactoryImpl)
        factory.assertFieldEquals("okHttpClient", okHttpClient)
        factory.assertFieldEquals("logger", logger)
    }

    @Test
    fun `StreamHealthMonitor factory applies configuration`() {
        // Given
        val logger = mockk<StreamLogger>(relaxed = true)
        val scope = TestScope(dispatcher)
        val interval = 1_000L
        val liveness = 2_000L

        // When
        val monitor =
            StreamHealthMonitorFactory(
                logger = logger,
                scope = scope,
                interval = interval,
                livenessThreshold = liveness,
            )

        // Then
        assertTrue(monitor is StreamHealthMonitorImpl)
        monitor.assertFieldEquals("logger", logger)
        monitor.assertFieldEquals("scope", scope)
        monitor.assertFieldEquals("interval", interval)
        monitor.assertFieldEquals("livenessThreshold", liveness)
    }

    @Test
    fun `StreamSubscriptionManager factory propagates limits`() {
        // Given
        val logger = mockk<StreamLogger>(relaxed = true)
        val maxStrong = 3
        val maxWeak = 4

        // When
        val manager =
            StreamSubscriptionManagerFactory<StreamSubscription>(
                logger = logger,
                maxStrongSubscriptions = maxStrong,
                maxWeakSubscriptions = maxWeak,
            )

        // Then
        assertTrue(manager is StreamSubscriptionManagerImpl)
        manager.assertFieldEquals("logger", logger)
        manager.assertFieldEquals("maxStrongSubscriptions", maxStrong)
        manager.assertFieldEquals("maxWeakSubscriptions", maxWeak)
    }

    @Test
    fun `StreamConnectionIdHolder factory returns implementation`() {
        // Given

        // When
        val holder = StreamConnectionIdHolderFactory()

        // Then
        assertTrue(holder is StreamConnectionIdHolderImpl)
    }

    @Test
    fun `StreamTokenManager factory wires dependencies`() {
        // Given
        val userId = StreamUserId.fromString("user-123")
        val tokenProvider = mockk<StreamTokenProvider>(relaxed = true)
        val singleFlight = StreamSingleFlightProcessorFactory(scope = testScope)

        // When
        val manager = StreamTokenManagerFactory(userId, tokenProvider, singleFlight)

        // Then
        assertTrue(manager is StreamTokenManagerImpl)
        manager.assertFieldEquals("userId", userId.rawValue)
        manager.assertFieldEquals("tokenProvider", tokenProvider)
        manager.assertFieldEquals("singleFlight", singleFlight)
    }
}
