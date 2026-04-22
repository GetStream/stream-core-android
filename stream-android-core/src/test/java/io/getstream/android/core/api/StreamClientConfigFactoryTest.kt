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

@file:OptIn(StreamInternalApi::class)

package io.getstream.android.core.api

import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.telephony.TelephonyManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import io.getstream.android.core.annotations.StreamInternalApi
import io.getstream.android.core.api.authentication.StreamTokenManager
import io.getstream.android.core.api.authentication.StreamTokenProvider
import io.getstream.android.core.api.components.StreamAndroidComponentsProvider
import io.getstream.android.core.api.log.StreamLogger
import io.getstream.android.core.api.log.StreamLoggerProvider
import io.getstream.android.core.api.model.StreamUser
import io.getstream.android.core.api.model.config.StreamClientSerializationConfig
import io.getstream.android.core.api.model.config.StreamComponentProvider
import io.getstream.android.core.api.model.config.StreamSocketConfig
import io.getstream.android.core.api.model.connection.StreamConnectionState
import io.getstream.android.core.api.model.value.StreamApiKey
import io.getstream.android.core.api.model.value.StreamHttpClientInfoHeader
import io.getstream.android.core.api.model.value.StreamToken
import io.getstream.android.core.api.model.value.StreamUserId
import io.getstream.android.core.api.model.value.StreamWsUrl
import io.getstream.android.core.api.processing.StreamEventAggregator
import io.getstream.android.core.api.processing.StreamSerialProcessingQueue
import io.getstream.android.core.api.processing.StreamSingleFlightProcessor
import io.getstream.android.core.api.serialization.StreamEventSerialization
import io.getstream.android.core.api.socket.StreamConnectionIdHolder
import io.getstream.android.core.api.socket.StreamWebSocketFactory
import io.getstream.android.core.api.socket.listeners.StreamClientListener
import io.getstream.android.core.api.socket.monitor.StreamHealthMonitor
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager
import io.getstream.android.core.internal.client.StreamClientImpl
import io.getstream.android.core.internal.socket.StreamSocketSession
import io.getstream.android.core.testutil.assertFieldEquals
import io.getstream.android.core.testutil.readPrivateField
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Test

internal class StreamClientConfigFactoryTest {

    private val dispatcher = StandardTestDispatcher()
    private val testScope = TestScope(dispatcher)
    private val productSerializer = mockk<StreamEventSerialization<Any>>(relaxed = true)
    private val serializationConfig = StreamClientSerializationConfig.default(productSerializer)
    private val logProvider =
        object : StreamLoggerProvider {
            override fun taggedLogger(tag: String): StreamLogger =
                object : StreamLogger {
                    override fun log(
                        level: StreamLogger.LogLevel,
                        throwable: Throwable?,
                        message: () -> String,
                    ) {
                        // no-op for tests
                    }
                }
        }

    private val fakeAndroidComponents =
        object : StreamAndroidComponentsProvider {
            override fun connectivityManager(): Result<ConnectivityManager> =
                Result.success(mockk(relaxed = true))

            override fun wifiManager(): Result<WifiManager> = Result.success(mockk(relaxed = true))

            override fun telephonyManager(): Result<TelephonyManager> =
                Result.success(mockk(relaxed = true))

            override fun lifecycle(): Lifecycle =
                object : Lifecycle() {
                    override fun addObserver(observer: LifecycleObserver) {}

                    override fun removeObserver(observer: LifecycleObserver) {}

                    override val currentState: State
                        get() = State.CREATED
                }
        }

    private val defaultSocketConfig =
        StreamSocketConfig.jwt(
            url = StreamWsUrl.fromString("wss://test.stream/connect"),
            apiKey = StreamApiKey.fromString("key123"),
            clientInfoHeader =
                StreamHttpClientInfoHeader.create(
                    product = "android",
                    productVersion = "1.0",
                    os = "android",
                    apiLevel = 33,
                    deviceModel = "Pixel",
                    app = "test-app",
                    appVersion = "1.0.0",
                ),
        )
    private val user = StreamUser(id = StreamUserId.fromString("user-123"))

    private fun buildClient(
        socketConfig: StreamSocketConfig = defaultSocketConfig,
        components: StreamComponentProvider =
            StreamComponentProvider(
                logProvider = logProvider,
                androidComponentsProvider = fakeAndroidComponents,
            ),
    ): StreamClient =
        StreamClient(
            scope = testScope,
            context = mockk(relaxed = true),
            user = user,
            tokenProvider =
                object : StreamTokenProvider {
                    override suspend fun loadToken(userId: StreamUserId): StreamToken =
                        StreamToken.fromString("token")
                },
            products = listOf("feeds"),
            socketConfig = socketConfig,
            serializationConfig = serializationConfig,
            components = components,
        )

    // ── StreamSocketConfig tunables ─────────────────────────────────────────

    @Test
    fun `factory with default config creates client in Idle state`() {
        val client = buildClient()

        assertTrue(client is StreamClientImpl<*>)
        assertTrue(client.connectionState.value is StreamConnectionState.Idle)
    }

    @Test
    fun `factory wires socketConfig to socket session`() {
        val customConfig =
            StreamSocketConfig.jwt(
                url = StreamWsUrl.fromString("wss://staging.getstream.io"),
                apiKey = StreamApiKey.fromString("staging-key"),
                clientInfoHeader = defaultSocketConfig.clientInfoHeader,
            )
        val client = buildClient(socketConfig = customConfig)

        val socketSession =
            (client as StreamClientImpl<*>).readPrivateField("socketSession")
                as StreamSocketSession<*>
        socketSession.assertFieldEquals("config", customConfig)
    }

    @Test
    fun `factory wires custom health check timing from socketConfig`() {
        val customConfig =
            StreamSocketConfig.jwt(
                url = defaultSocketConfig.url,
                apiKey = defaultSocketConfig.apiKey,
                clientInfoHeader = defaultSocketConfig.clientInfoHeader,
                healthCheckIntervalMs = 5_000L,
                livenessThresholdMs = 15_000L,
            )
        val client = buildClient(socketConfig = customConfig)

        val socketSession =
            (client as StreamClientImpl<*>).readPrivateField("socketSession")
                as StreamSocketSession<*>
        val healthMonitor = socketSession.readPrivateField("healthMonitor") as StreamHealthMonitor
        assertNotNull(healthMonitor)
        healthMonitor.assertFieldEquals("interval", 5_000L)
        healthMonitor.assertFieldEquals("livenessThreshold", 15_000L)
    }

    @Test
    fun `factory wires custom aggregation parameters from socketConfig`() {
        val customConfig =
            StreamSocketConfig.jwt(
                url = defaultSocketConfig.url,
                apiKey = defaultSocketConfig.apiKey,
                clientInfoHeader = defaultSocketConfig.clientInfoHeader,
                aggregationThreshold = 20,
                aggregationMaxWindowMs = 200L,
                aggregationDispatchQueueCapacity = 8,
            )
        val client = buildClient(socketConfig = customConfig)

        val socketSession =
            (client as StreamClientImpl<*>).readPrivateField("socketSession")
                as StreamSocketSession<*>
        val aggregator = socketSession.readPrivateField("aggregator")
        assertNotNull(aggregator)
    }

    // ── StreamComponentProvider overrides ────────────────────────────────────

    @Test
    fun `factory wires injected singleFlight from components`() {
        val singleFlight = mockk<StreamSingleFlightProcessor>(relaxed = true)
        val client =
            buildClient(
                components =
                    StreamComponentProvider(
                        logProvider = logProvider,
                        singleFlight = singleFlight,
                        androidComponentsProvider = fakeAndroidComponents,
                    )
            )

        (client as StreamClientImpl<*>).assertFieldEquals("singleFlight", singleFlight)
    }

    @Test
    fun `factory wires injected serialQueue from components`() {
        val serialQueue = mockk<StreamSerialProcessingQueue>(relaxed = true)
        val client =
            buildClient(
                components =
                    StreamComponentProvider(
                        logProvider = logProvider,
                        serialQueue = serialQueue,
                        androidComponentsProvider = fakeAndroidComponents,
                    )
            )

        (client as StreamClientImpl<*>).assertFieldEquals("serialQueue", serialQueue)
    }

    @Test
    fun `factory wires injected tokenManager from components`() {
        val tokenManager = mockk<StreamTokenManager>(relaxed = true)
        val client =
            buildClient(
                components =
                    StreamComponentProvider(
                        logProvider = logProvider,
                        tokenManager = tokenManager,
                        androidComponentsProvider = fakeAndroidComponents,
                    )
            )

        (client as StreamClientImpl<*>).assertFieldEquals("tokenManager", tokenManager)
    }

    @Test
    fun `factory wires injected connectionIdHolder from components`() {
        val connectionIdHolder = StreamConnectionIdHolder()
        val client =
            buildClient(
                components =
                    StreamComponentProvider(
                        logProvider = logProvider,
                        connectionIdHolder = connectionIdHolder,
                        androidComponentsProvider = fakeAndroidComponents,
                    )
            )

        (client as StreamClientImpl<*>).assertFieldEquals("connectionIdHolder", connectionIdHolder)
    }

    @Test
    fun `factory wires injected socketFactory from components`() {
        val socketFactory = mockk<StreamWebSocketFactory>(relaxed = true)
        val client =
            buildClient(
                components =
                    StreamComponentProvider(
                        logProvider = logProvider,
                        socketFactory = socketFactory,
                        androidComponentsProvider = fakeAndroidComponents,
                    )
            )

        val socketSession =
            (client as StreamClientImpl<*>).readPrivateField("socketSession")
                as StreamSocketSession<*>
        val internalSocket = socketSession.readPrivateField("internalSocket")
        val wiredFactory = internalSocket?.readPrivateField("socketFactory")
        assertEquals(socketFactory, wiredFactory)
    }

    @Test
    fun `factory wires injected healthMonitor from components`() {
        val healthMonitor = mockk<StreamHealthMonitor>(relaxed = true)
        val client =
            buildClient(
                components =
                    StreamComponentProvider(
                        logProvider = logProvider,
                        healthMonitor = healthMonitor,
                        androidComponentsProvider = fakeAndroidComponents,
                    )
            )

        val socketSession =
            (client as StreamClientImpl<*>).readPrivateField("socketSession")
                as StreamSocketSession<*>
        socketSession.assertFieldEquals("healthMonitor", healthMonitor)
    }

    @Test
    fun `factory wires injected aggregator from components`() {
        val eventAggregator = mockk<StreamEventAggregator<Any>>(relaxed = true)
        val client =
            buildClient(
                components =
                    StreamComponentProvider(
                        logProvider = logProvider,
                        eventAggregator = eventAggregator,
                        androidComponentsProvider = fakeAndroidComponents,
                    )
            )

        val socketSession =
            (client as StreamClientImpl<*>).readPrivateField("socketSession")
                as StreamSocketSession<*>
        socketSession.assertFieldEquals("aggregator", eventAggregator)
    }

    @Test
    fun `factory wires injected clientSubscriptionManager from components`() {
        val subscriptionManager =
            mockk<StreamSubscriptionManager<StreamClientListener>>(relaxed = true)
        val client =
            buildClient(
                components =
                    StreamComponentProvider(
                        logProvider = logProvider,
                        clientSubscriptionManager = subscriptionManager,
                        androidComponentsProvider = fakeAndroidComponents,
                    )
            )

        (client as StreamClientImpl<*>).assertFieldEquals(
            "subscriptionManager",
            subscriptionManager,
        )
    }

    @Test
    fun `factory creates default components when provider fields are null`() {
        val client =
            buildClient(
                components =
                    StreamComponentProvider(
                        logProvider = logProvider,
                        androidComponentsProvider = fakeAndroidComponents,
                    )
            )

        val impl = client as StreamClientImpl<*>
        assertNotNull(impl.readPrivateField("singleFlight"))
        assertNotNull(impl.readPrivateField("serialQueue"))
        assertNotNull(impl.readPrivateField("tokenManager"))
        assertNotNull(impl.readPrivateField("connectionIdHolder"))
        assertNotNull(impl.readPrivateField("subscriptionManager"))

        val socketSession = impl.readPrivateField("socketSession") as StreamSocketSession<*>
        assertNotNull(socketSession.readPrivateField("healthMonitor"))
        assertNotNull(socketSession.readPrivateField("aggregator"))
        assertNotNull(socketSession.readPrivateField("internalSocket"))
    }

    // ── SocketConfig + Components combined ──────────────────────────────────

    @Test
    fun `factory applies both socketConfig tunables and component overrides`() {
        val singleFlight = mockk<StreamSingleFlightProcessor>(relaxed = true)
        val healthMonitor = mockk<StreamHealthMonitor>(relaxed = true)
        val customSocketConfig =
            StreamSocketConfig.jwt(
                url = StreamWsUrl.fromString("wss://custom.stream.io"),
                apiKey = defaultSocketConfig.apiKey,
                clientInfoHeader = defaultSocketConfig.clientInfoHeader,
                aggregationThreshold = 10,
                aggregationMaxWindowMs = 200L,
                aggregationDispatchQueueCapacity = 8,
            )

        val client =
            buildClient(
                socketConfig = customSocketConfig,
                components =
                    StreamComponentProvider(
                        logProvider = logProvider,
                        singleFlight = singleFlight,
                        healthMonitor = healthMonitor,
                        androidComponentsProvider = fakeAndroidComponents,
                    ),
            )

        val impl = client as StreamClientImpl<*>
        impl.assertFieldEquals("singleFlight", singleFlight)

        val socketSession = impl.readPrivateField("socketSession") as StreamSocketSession<*>
        socketSession.assertFieldEquals("config", customSocketConfig)

        // Injected health monitor takes precedence over socketConfig timing
        socketSession.assertFieldEquals("healthMonitor", healthMonitor)

        // Aggregator still created from socketConfig since not injected
        val aggregator = socketSession.readPrivateField("aggregator")
        assertNotNull(aggregator)
    }
}
