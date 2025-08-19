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
@file:OptIn(
    kotlin.time.ExperimentalTime::class,
    kotlinx.coroutines.ExperimentalCoroutinesApi::class,
)

package io.getstream.android.core.internal.client

import io.getstream.android.core.api.StreamClient
import io.getstream.android.core.api.authentication.StreamTokenManager
import io.getstream.android.core.api.authentication.StreamTokenProvider
import io.getstream.android.core.api.log.StreamLogger
import io.getstream.android.core.api.log.StreamLoggerProvider
import io.getstream.android.core.api.model.config.StreamEndpointConfig
import io.getstream.android.core.api.model.value.StreamApiKey
import io.getstream.android.core.api.model.value.StreamUserId
import io.getstream.android.core.api.processing.StreamDebounceMessageProcessor
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
import io.getstream.android.core.internal.processing.StreamDebounceProcessorImpl
import io.getstream.android.core.internal.processing.StreamSerialProcessingQueueImpl
import io.getstream.android.core.internal.processing.StreamSingleFlightProcessorImpl
import io.getstream.android.core.internal.serialization.StreamMoshiJsonSerializationImpl
import io.getstream.android.core.internal.socket.StreamClientEventsParser
import io.getstream.android.core.internal.socket.StreamSocketSession
import io.getstream.android.core.internal.socket.StreamWebSocketImpl
import io.getstream.android.core.internal.socket.connection.StreamConnectionIdHolderImpl
import io.getstream.android.core.internal.socket.factory.StreamWebSocketFactoryImpl
import io.getstream.android.core.internal.socket.monitor.StreamHealthMonitorImpl
import io.getstream.android.core.internal.state.StreamClientStateImpl
import io.getstream.android.core.internal.subscribe.StreamSubscriptionManagerImpl
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Test

private fun Any.getPrivate(name: String): Any? =
    this::class.java.getDeclaredField(name).apply { isAccessible = true }.get(this)

class StreamClientTest {

    private fun testScope() = TestScope(UnconfinedTestDispatcher())

    private fun loggerProvider(): StreamLoggerProvider =
        mockk(relaxed = true) {
            every { taggedLogger(any()) } returns mockk<StreamLogger>(relaxed = true)
        }

    private fun endpoint(): StreamEndpointConfig =
        mockk(relaxed = true) {
            every { wsUrl } returns "wss://example.test/socket"
            every { clientInfoHeader } returns mockk(relaxed = true)
        }

    @Test
    fun `createClient uses defaults when optional args are null`() {
        val apiKey = mockk<StreamApiKey>(relaxed = true)
        val userId = mockk<StreamUserId>(relaxed = true)
        val ep = endpoint()
        val tp = mockk<StreamTokenProvider>(relaxed = true)
        val scope = testScope()
        val logs = loggerProvider()

        val client: StreamClient<StreamClientState> =
            createClient(
                apiKey = apiKey,
                userId = userId,
                endpoint = ep,
                clientSubscriptionManager = null,
                tokenProvider = tp,
                tokenManager = null,
                singleFlightProcessor = null,
                serialProcessingQueue = null,
                retryProcessor = null,
                httpInterceptors = emptyList(),
                connectionIdHolder = null,
                socketFactory = null,
                healthMonitor = null,
                socketSubscriptionManager = null,
                socketEventDebounceProcessor = null,
                json = null,
                logProvider = logs,
                scope = scope,
            )

        // Downcast & inspect internals
        assertTrue(client is StreamClientIImpl)
        // state type
        assertTrue(client.state is StreamClientStateImpl)

        val serialQueue = client.getPrivate("serialQueue")
        assertTrue(serialQueue is StreamSerialProcessingQueueImpl)

        val singleFlight = client.getPrivate("singleFlight")
        assertTrue(singleFlight is StreamSingleFlightProcessorImpl)

        val tokenManager = client.getPrivate("tokenManager")
        assertTrue(
            tokenManager is StreamTokenManager
        ) // concrete is StreamTokenManagerImpl, interface is enough

        val connHolder = client.getPrivate("connectionIdHolder")
        assertTrue(connHolder is StreamConnectionIdHolderImpl)

        val session = client.getPrivate("socketSession") as StreamSocketSession

        val health = session.getPrivate("healthMonitor")
        assertTrue(health is StreamHealthMonitorImpl)

        val debounce = session.getPrivate("debounceProcessor")
        assertTrue(debounce is StreamDebounceProcessorImpl<*>)

        val json = session.getPrivate("jsonSerialization")
        assertTrue(json is StreamMoshiJsonSerializationImpl)

        val parser = session.getPrivate("eventParser") as StreamClientEventsParser
        val parserJson = parser.getPrivate("jsonParser")
        assertSame(json, parserJson)

        val internalSocket = session.getPrivate("internalSocket") as StreamWebSocketImpl<*>
        val socketFactory = internalSocket.getPrivate("socketFactory")
        assertTrue(socketFactory is StreamWebSocketFactoryImpl)

        val socketSubsMgr = internalSocket.getPrivate("subscriptionManager")
        assertTrue(socketSubsMgr is StreamSubscriptionManagerImpl<*>)

        val clientSubsMgr = client.getPrivate("subscriptionManager")
        assertTrue(clientSubsMgr is StreamSubscriptionManagerImpl<*>)
    }

    @Test
    fun `createClient uses supplied dependencies when provided`() {
        val apiKey = mockk<StreamApiKey>(relaxed = true)
        val userId = mockk<StreamUserId>(relaxed = true)
        val ep = endpoint()
        val tp = mockk<StreamTokenProvider>(relaxed = true)
        val scope = testScope()
        val logs = loggerProvider()

        val customTokenMgr = mockk<StreamTokenManager>(relaxed = true)
        val customSF = mockk<StreamSingleFlightProcessor>(relaxed = true)
        val customSerial = mockk<StreamSerialProcessingQueue>(relaxed = true)
        val customConnHolder = mockk<StreamConnectionIdHolder>(relaxed = true)
        val customJson = mockk<StreamJsonSerialization>(relaxed = true)
        val customHealth = mockk<StreamHealthMonitor>(relaxed = true)
        val customDebounce = mockk<StreamDebounceMessageProcessor<String>>(relaxed = true)
        val customSocketFactory = mockk<StreamWebSocketFactory>(relaxed = true)
        val customSocketSubsMgr =
            mockk<StreamSubscriptionManager<StreamWebSocketListener>>(relaxed = true)
        val customClientSubsMgr =
            mockk<StreamSubscriptionManager<StreamClientListener>>(relaxed = true)

        val client: StreamClient<StreamClientState> =
            createClient(
                apiKey = apiKey,
                userId = userId,
                endpoint = ep,
                clientSubscriptionManager = customClientSubsMgr,
                tokenProvider = tp,
                tokenManager = customTokenMgr,
                singleFlightProcessor = customSF,
                serialProcessingQueue = customSerial,
                retryProcessor = null, // not observable downstream
                httpInterceptors = emptyList(),
                connectionIdHolder = customConnHolder,
                socketFactory = customSocketFactory,
                healthMonitor = customHealth,
                socketSubscriptionManager = customSocketSubsMgr,
                socketEventDebounceProcessor = customDebounce,
                json = customJson,
                logProvider = logs,
                scope = scope,
            )

        assertTrue(client is StreamClientIImpl)

        // Top-level dependencies
        assertSame(customSerial, client.getPrivate("serialQueue"))
        assertSame(customSF, client.getPrivate("singleFlight"))
        assertSame(customTokenMgr, client.getPrivate("tokenManager"))
        assertSame(customConnHolder, client.getPrivate("connectionIdHolder"))
        assertSame(customClientSubsMgr, client.getPrivate("subscriptionManager"))

        // Session internals reflect injected dependencies
        val session = client.getPrivate("socketSession") as StreamSocketSession
        assertSame(customHealth, session.getPrivate("healthMonitor"))
        assertSame(customDebounce, session.getPrivate("debounceProcessor"))
        assertSame(customJson, session.getPrivate("jsonSerialization"))

        val parser = session.getPrivate("eventParser") as StreamClientEventsParser
        assertSame(customJson, parser.getPrivate("jsonParser"))

        val internalSocket = session.getPrivate("internalSocket") as StreamWebSocketImpl<*>
        assertSame(customSocketFactory, internalSocket.getPrivate("socketFactory"))
        assertSame(customSocketSubsMgr, internalSocket.getPrivate("subscriptionManager"))

        // State type still correct
        assertTrue(client.state is StreamClientStateImpl)
    }

    @Test
    fun `invokeOnCompletion cancels and cleans up provided components when parent scope is cancelled`() {
        val parentJob = Job()
        val scope = CoroutineScope(UnconfinedTestDispatcher() + parentJob)

        val apiKey = mockk<StreamApiKey>(relaxed = true)
        val userId = mockk<StreamUserId>(relaxed = true)
        val ep = endpoint()
        val tp = mockk<StreamTokenProvider>(relaxed = true)
        val logs = loggerProvider()

        // Provide custom components so we can verify they are cleaned up
        val customTokenMgr = mockk<StreamTokenManager>(relaxed = true)
        val customSF = mockk<StreamSingleFlightProcessor>(relaxed = true)
        val customSerial = mockk<StreamSerialProcessingQueue>(relaxed = true)
        val customConnHolder = mockk<StreamConnectionIdHolder>(relaxed = true)
        val customHealth = mockk<StreamHealthMonitor>(relaxed = true)
        val customDebounce = mockk<StreamDebounceMessageProcessor<String>>(relaxed = true)
        val customSocketFactory = mockk<StreamWebSocketFactory>(relaxed = true)
        val customSocketSubsMgr =
            mockk<StreamSubscriptionManager<StreamWebSocketListener>>(relaxed = true)
        val customClientSubsMgr =
            mockk<StreamSubscriptionManager<StreamClientListener>>(relaxed = true)

        // Stubs for cleanup path
        // serialQueue.stop() is called twice in the onCompletion block.
        coJustRun { customSerial.stop(any()) }
        // debounce.stop() is called inside runBlocking
        coEvery { customDebounce.stop() } returns Result.success(Unit)
        // healthMonitor.stop() is called inside runBlocking (non-suspend is fine)
        justRun { customHealth.stop() }
        // singleFlight.clear(true)
        every { customSF.clear(true) } returns Result.success(Unit)

        // Build the client
        val client: StreamClient<StreamClientState> =
            createClient(
                apiKey = apiKey,
                userId = userId,
                endpoint = ep,
                clientSubscriptionManager = customClientSubsMgr,
                tokenProvider = tp,
                tokenManager = customTokenMgr,
                singleFlightProcessor = customSF,
                serialProcessingQueue = customSerial,
                retryProcessor = null,
                httpInterceptors = emptyList(),
                connectionIdHolder = customConnHolder,
                socketFactory = customSocketFactory,
                healthMonitor = customHealth,
                socketSubscriptionManager = customSocketSubsMgr,
                socketEventDebounceProcessor = customDebounce,
                json = mockk(relaxed = true),
                logProvider = logs,
                scope = scope,
            )

        // Act: cancel parent scope -> should complete child SupervisorJob and run cleanup
        parentJob.cancel()

        // Assert: all cleanup hooks were invoked
        coVerify(exactly = 2) { customSerial.stop(null) }
        verify(exactly = 1) { customSF.clear(true) }
        coVerify(exactly = 1) { customDebounce.stop() }
        verify(exactly = 1) { customHealth.stop() }

        // The absence of exceptions and the verifies ensure the invokeOnCompletion block ran.

    }
}
