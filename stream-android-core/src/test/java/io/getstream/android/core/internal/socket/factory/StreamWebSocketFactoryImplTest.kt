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

package io.getstream.android.core.internal.socket.factory

import io.getstream.android.core.api.log.StreamLogger
import io.getstream.android.core.api.model.config.StreamSocketConfig
import io.getstream.android.core.api.model.value.StreamApiKey
import io.getstream.android.core.api.model.value.StreamHttpClientInfoHeader
import io.mockk.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class StreamWebSocketFactoryImplTest {

    private lateinit var okHttpClient: OkHttpClient
    private lateinit var logger: StreamLogger
    private lateinit var factory: StreamWebSocketFactoryImpl
    private lateinit var listener: WebSocketListener
    private lateinit var webSocket: WebSocket

    val clientInfoHeader =
        StreamHttpClientInfoHeader.create(
            product = "test-product",
            productVersion = "1.0",
            app = "test-app",
            appVersion = "1.0",
            os = "test-os",
            apiLevel = 1,
            deviceModel = "test-device",
        )

    val clientInfoHeaderRaw = clientInfoHeader.rawValue

    private val config =
        StreamSocketConfig.jwt(
            url = "wss://example.com/connect",
            apiKey = StreamApiKey.fromString("test-key"),
            clientInfoHeader = clientInfoHeader,
        )

    @Before
    fun setup() {
        okHttpClient = mockk()
        logger = mockk(relaxed = true)
        factory = StreamWebSocketFactoryImpl(okHttpClient, logger)
        listener = mockk()
        webSocket = mockk()
    }

    @Test
    fun `create builds request with correct url and headers`() {
        // Arrange
        every { okHttpClient.newWebSocket(any(), any()) } returns webSocket

        // Act
        val result = factory.create(config, listener)

        // Assert
        assertTrue(result.isSuccess)
        assertSame(webSocket, result.getOrThrow())

        // Capture the request passed to OkHttp
        val slot = slot<Request>()
        verify { okHttpClient.newWebSocket(capture(slot), listener) }

        val request = slot.captured
        val url = request.url.toString()
        assertTrue(url.startsWith("https://example.com/connect"))
        assertTrue(url.contains("api_key=test-key"))
        assertTrue(url.contains("stream-auth-type=jwt"))
        assertTrue(url.contains("X-Stream-Client=${clientInfoHeaderRaw}"))

        assertEquals("Upgrade", request.header("Connection"))
        assertEquals("websocket", request.header("Upgrade"))
        assertEquals(clientInfoHeaderRaw, request.header("X-Stream-Client"))

        verify { logger.v(any()) }
    }

    @Test
    fun `create returns failure when OkHttp throws`() {
        // Arrange
        every { okHttpClient.newWebSocket(any(), any()) } throws RuntimeException("boom")

        // Act
        val result = factory.create(config, listener)

        // Assert
        assertTrue(result.isFailure)
        assertEquals("boom", result.exceptionOrNull()!!.message)
    }
}
