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

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import io.getstream.android.core.annotations.StreamInternalApi
import io.getstream.android.core.api.authentication.StreamTokenManager
import io.getstream.android.core.api.authentication.StreamTokenProvider
import io.getstream.android.core.api.log.StreamLogger
import io.getstream.android.core.api.log.StreamLoggerProvider
import io.getstream.android.core.api.model.config.StreamClientSerializationConfig
import io.getstream.android.core.api.model.config.StreamHttpConfig
import io.getstream.android.core.api.model.config.StreamSocketConfig
import io.getstream.android.core.api.model.connection.StreamConnectionState
import io.getstream.android.core.api.model.value.StreamApiKey
import io.getstream.android.core.api.model.value.StreamHttpClientInfoHeader
import io.getstream.android.core.api.model.value.StreamToken
import io.getstream.android.core.api.model.value.StreamUserId
import io.getstream.android.core.api.model.value.StreamWsUrl
import io.getstream.android.core.api.observers.lifecycle.StreamLifecycleMonitor
import io.getstream.android.core.api.observers.network.StreamNetworkMonitor
import io.getstream.android.core.api.processing.StreamBatcher
import io.getstream.android.core.api.processing.StreamRetryProcessor
import io.getstream.android.core.api.processing.StreamSerialProcessingQueue
import io.getstream.android.core.api.processing.StreamSingleFlightProcessor
import io.getstream.android.core.api.recovery.StreamConnectionRecoveryEvaluator
import io.getstream.android.core.api.serialization.StreamEventSerialization
import io.getstream.android.core.api.socket.StreamConnectionIdHolder
import io.getstream.android.core.api.socket.StreamWebSocketFactory
import io.getstream.android.core.api.socket.listeners.StreamClientListener
import io.getstream.android.core.api.socket.monitor.StreamHealthMonitor
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager
import io.getstream.android.core.internal.client.StreamClientImpl
import io.getstream.android.core.internal.http.interceptor.StreamApiKeyInterceptor
import io.getstream.android.core.internal.http.interceptor.StreamAuthInterceptor
import io.getstream.android.core.internal.http.interceptor.StreamClientInfoInterceptor
import io.getstream.android.core.internal.http.interceptor.StreamConnectionIdInterceptor
import io.getstream.android.core.internal.http.interceptor.StreamEndpointErrorInterceptor
import io.getstream.android.core.internal.serialization.StreamCompositeEventSerializationImpl
import io.getstream.android.core.internal.socket.StreamSocketSession
import io.getstream.android.core.internal.socket.StreamWebSocketImpl
import io.getstream.android.core.internal.subscribe.StreamSubscriptionManagerImpl
import io.getstream.android.core.testutil.assertFieldEquals
import io.getstream.android.core.testutil.readPrivateField
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.junit.Test

internal class StreamClientFactoryTest {

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

    private data class Dependencies(
        val apiKey: StreamApiKey,
        val userId: StreamUserId,
        val wsUrl: StreamWsUrl,
        val clientInfo: StreamHttpClientInfoHeader,
        val clientSubscriptionManager: StreamSubscriptionManager<StreamClientListener>,
        val tokenProvider: StreamTokenProvider,
        val tokenManager: StreamTokenManager,
        val singleFlight: StreamSingleFlightProcessor,
        val serialQueue: StreamSerialProcessingQueue,
        val retryProcessor: StreamRetryProcessor,
        val connectionIdHolder: StreamConnectionIdHolder,
        val socketFactory: StreamWebSocketFactory,
        val healthMonitor: StreamHealthMonitor,
        val batcher: StreamBatcher<String>,
        val lifecycleMonitor: StreamLifecycleMonitor,
        val networkMonitor: StreamNetworkMonitor,
        val connectionRecoveryEvaluator: StreamConnectionRecoveryEvaluator,
    )

    private fun createClient(
        httpConfig: StreamHttpConfig? = null
    ): Pair<StreamClient, Dependencies> {
        val deps =
            Dependencies(
                apiKey = StreamApiKey.fromString("key123"),
                userId = StreamUserId.fromString("user-123"),
                wsUrl = StreamWsUrl.fromString("wss://test.stream/video"),
                clientInfo =
                    StreamHttpClientInfoHeader.create(
                        product = "android",
                        productVersion = "1.0",
                        os = "android",
                        apiLevel = 33,
                        deviceModel = "Pixel",
                        app = "test-app",
                        appVersion = "1.0.0",
                    ),
                clientSubscriptionManager = mockk(relaxed = true),
                tokenProvider = mockk(relaxed = true),
                tokenManager = mockk(relaxed = true),
                singleFlight = mockk(relaxed = true),
                serialQueue = mockk(relaxed = true),
                retryProcessor = mockk(relaxed = true),
                connectionIdHolder = mockk(relaxed = true),
                socketFactory = mockk(relaxed = true),
                healthMonitor = mockk(relaxed = true),
                batcher = mockk(relaxed = true),
                lifecycleMonitor = mockk(relaxed = true),
                networkMonitor = mockk(relaxed = true),
                connectionRecoveryEvaluator = mockk(relaxed = true),
            )

        val client =
            StreamClient(
                context = mockk(relaxed = true),
                apiKey = deps.apiKey,
                userId = deps.userId,
                wsUrl = deps.wsUrl,
                products = listOf("feeds"),
                clientInfoHeader = deps.clientInfo,
                clientSubscriptionManager = deps.clientSubscriptionManager,
                tokenProvider = deps.tokenProvider,
                tokenManager = deps.tokenManager,
                singleFlight = deps.singleFlight,
                serialQueue = deps.serialQueue,
                retryProcessor = deps.retryProcessor,
                scope = testScope,
                connectionIdHolder = deps.connectionIdHolder,
                socketFactory = deps.socketFactory,
                healthMonitor = deps.healthMonitor,
                batcher = deps.batcher,
                httpConfig = httpConfig,
                serializationConfig = serializationConfig,
                logProvider = logProvider,
                networkMonitor = deps.networkMonitor,
                lifecycleMonitor = deps.lifecycleMonitor,
                connectionRecoveryEvaluator = deps.connectionRecoveryEvaluator,
            )

        return client to deps
    }

    @Test
    fun `StreamClient factory wires core dependencies`() {
        // Given

        // When
        val (client, deps) = createClient()

        // Then
        assertTrue(client is StreamClientImpl<*>)
        assertTrue(client.connectionState.value is StreamConnectionState.Idle)

        // Verify client level wiring
        client.assertFieldEquals("userId", deps.userId.rawValue)
        client.assertFieldEquals("tokenManager", deps.tokenManager)
        client.assertFieldEquals("singleFlight", deps.singleFlight)
        client.assertFieldEquals("serialQueue", deps.serialQueue)
        client.assertFieldEquals("connectionIdHolder", deps.connectionIdHolder)
        client.assertFieldEquals("subscriptionManager", deps.clientSubscriptionManager)
        val scope = client.readPrivateField("scope") as CoroutineScope

        assertNotSame(testScope, scope)

        // socket session wiring
        val socketSession = client.readPrivateField("socketSession") as StreamSocketSession<*>
        val expectedConfig =
            StreamSocketConfig.jwt(
                url = deps.wsUrl.rawValue,
                apiKey = deps.apiKey,
                clientInfoHeader = deps.clientInfo,
            )
        socketSession.assertFieldEquals("config", expectedConfig)
        socketSession.assertFieldEquals("healthMonitor", deps.healthMonitor)
        socketSession.assertFieldEquals("batcher", deps.batcher)
        socketSession.assertFieldEquals("products", listOf("feeds"))

        val internalSocket = socketSession.readPrivateField("internalSocket")
        assertTrue(internalSocket is StreamWebSocketImpl<*>)

        val eventParser = socketSession.readPrivateField("eventParser")
        assertTrue(eventParser is StreamCompositeEventSerializationImpl<*>)

        val sessionSubscriptionManager = socketSession.readPrivateField("subscriptionManager")
        assertTrue(sessionSubscriptionManager is StreamSubscriptionManager<*>)
    }

    @Test
    fun `StreamClient factory attaches automatic and custom http interceptors`() {
        // Given
        val builder = OkHttpClient.Builder()
        val customInterceptor = Interceptor { chain -> chain.proceed(chain.request()) }
        val httpConfig =
            StreamHttpConfig(
                httpBuilder = builder,
                automaticInterceptors = true,
                configuredInterceptors = setOf(customInterceptor),
            )

        // When
        val (client, deps) = createClient(httpConfig)
        val interceptors = builder.interceptors()

        // Then
        assertEquals(6, interceptors.size)
        assertTrue(interceptors[0] is StreamClientInfoInterceptor)
        assertTrue(interceptors[1] is StreamApiKeyInterceptor)
        assertTrue(interceptors[2] is StreamConnectionIdInterceptor)
        assertTrue(interceptors[3] is StreamAuthInterceptor)
        assertTrue(interceptors[4] is StreamEndpointErrorInterceptor)
        assertEquals(customInterceptor, interceptors[5])

        val clientInfoInterceptor = interceptors[0] as StreamClientInfoInterceptor
        val storedClientInfo = clientInfoInterceptor.readPrivateField("clientInfo")
        when (storedClientInfo) {
            is String -> assertEquals(deps.clientInfo.rawValue, storedClientInfo)
            else -> assertEquals(deps.clientInfo, storedClientInfo)
        }

        val apiKeyInterceptor = interceptors[1] as StreamApiKeyInterceptor
        val storedApiKey = apiKeyInterceptor.readPrivateField("apiKey")
        when (storedApiKey) {
            is String -> assertEquals(deps.apiKey.rawValue, storedApiKey)
            else -> assertEquals(deps.apiKey, storedApiKey)
        }

        val connectionInterceptor = interceptors[2] as StreamConnectionIdInterceptor
        connectionInterceptor.assertFieldEquals("connectionIdHolder", deps.connectionIdHolder)

        val session =
            (client as StreamClientImpl<*>).readPrivateField("socketSession")
                as StreamSocketSession<*>
        val jsonSerialization = session.readPrivateField("jsonSerialization")

        val authInterceptor = interceptors[3] as StreamAuthInterceptor
        authInterceptor.assertFieldEquals("tokenManager", deps.tokenManager)
        authInterceptor.assertFieldEquals("authType", "jwt")
        assertEquals(jsonSerialization, authInterceptor.readPrivateField("jsonParser"))

        val errorInterceptor = interceptors[4] as StreamEndpointErrorInterceptor
        assertEquals(jsonSerialization, errorInterceptor.readPrivateField("jsonParser"))
    }

    @Test
    fun `StreamClient factory respects disabled automatic interceptors`() {
        // Given
        val builder = OkHttpClient.Builder()
        val customInterceptor = Interceptor { chain -> chain.proceed(chain.request()) }
        val httpConfig =
            StreamHttpConfig(
                httpBuilder = builder,
                automaticInterceptors = false,
                configuredInterceptors = setOf(customInterceptor),
            )

        // When
        createClient(httpConfig)

        // Then
        assertEquals(listOf(customInterceptor), builder.interceptors())
    }

    @Test
    fun `StreamClient factory default subscription manager listener limits`() {
        val context = mockk<android.content.Context>(relaxed = true)
        val fakeAndroidComponents =
            object : io.getstream.android.core.api.components.StreamAndroidComponentsProvider {
                override fun connectivityManager(): Result<android.net.ConnectivityManager> =
                    Result.success(mockk(relaxed = true))

                override fun wifiManager(): Result<android.net.wifi.WifiManager> =
                    Result.success(mockk(relaxed = true))

                override fun telephonyManager(): Result<android.telephony.TelephonyManager> =
                    Result.success(mockk(relaxed = true))

                override fun lifecycle(): Lifecycle =
                    object : Lifecycle() {
                        override fun addObserver(observer: LifecycleObserver) {}

                        override fun removeObserver(observer: LifecycleObserver) {}

                        override val currentState: Lifecycle.State
                            get() = Lifecycle.State.CREATED
                    }
            }
        val tokenProvider =
            object : StreamTokenProvider {
                override suspend fun loadToken(userId: StreamUserId): StreamToken =
                    StreamToken.fromString("token")
            }

        val client =
            StreamClient(
                scope = testScope,
                context = context,
                apiKey = StreamApiKey.fromString("key123"),
                userId = StreamUserId.fromString("user-123"),
                wsUrl = StreamWsUrl.fromString("wss://test.stream/video"),
                products = listOf("feeds"),
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
                tokenProvider = tokenProvider,
                serializationConfig = serializationConfig,
                androidComponentsProvider = fakeAndroidComponents,
                logProvider = logProvider,
            )

        val manager =
            (client as StreamClientImpl<*>).readPrivateField("subscriptionManager")
                as StreamSubscriptionManagerImpl<*>
        manager.assertFieldEquals("maxStrongSubscriptions", 250)
        manager.assertFieldEquals("maxWeakSubscriptions", 250)
    }
}
