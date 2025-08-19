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
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.getstream.android.core.internal.socket

import io.getstream.android.core.api.log.StreamLogger
import io.getstream.android.core.api.model.config.StreamSocketConfig
import io.getstream.android.core.api.socket.StreamWebSocketFactory
import io.getstream.android.core.api.socket.listeners.StreamWebSocketListener
import io.getstream.android.core.api.subscribe.StreamSubscription
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager
import io.mockk.CapturingSlot
import io.mockk.MockKAnnotations
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.io.IOException
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class StreamWebSocketImplTest {

    private lateinit var logger: StreamLogger
    private lateinit var factory: StreamWebSocketFactory
    private lateinit var subs: StreamSubscriptionManager<StreamWebSocketListener>

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        logger = mockk(relaxed = true)
        factory = mockk(relaxed = true)
        subs = mockk(relaxed = true)
    }

    private fun mockResponse(code: Int = 101): Response =
        Response.Builder()
            .request(Request.Builder().url("https://example.test").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("OK")
            .build()

    // --- open() ---

    @Test
    fun `open creates socket via factory and passes this as listener`() {
        val okWs = mockk<WebSocket>(relaxed = true)
        val listener: CapturingSlot<okhttp3.WebSocketListener> = slot()
        every { factory.create(any(), capture(listener)) } returns Result.success(okWs)

        val impl = StreamWebSocketImpl(logger, factory, subs)
        val cfg = mockk<StreamSocketConfig>(relaxed = true)

        val res = impl.open(cfg)
        assertTrue(res.isSuccess)
        assertSame(impl, listener.captured) // factory received this instance as listener
        verify { factory.create(cfg, any()) }
    }

    @Test
    fun `open failure leaves socket uninitialized (later operations fail)`() {
        every { factory.create(any(), any()) } returns Result.failure(IllegalStateException("boom"))
        val impl = StreamWebSocketImpl(logger, factory, subs)

        val res = impl.open(mockk(relaxed = true))
        assertTrue(res.isFailure)

        // Since not opened, withSocket{} should fail
        val sendText = impl.send("hi")
        val closeRes = impl.close()
        assertTrue(sendText.isFailure)
        assertTrue(closeRes.isFailure)
        assertTrue(sendText.exceptionOrNull() is IllegalStateException)
        assertTrue(closeRes.exceptionOrNull() is IllegalStateException)
    }

    // --- send(text) ---

    @Test
    fun `send text succeeds when underlying socket returns true`() {
        val okWs = mockk<WebSocket>(relaxed = true)
        every { factory.create(any(), any()) } returns Result.success(okWs)
        every { okWs.send("hello") } returns true

        val impl = StreamWebSocketImpl(logger, factory, subs)
        impl.open(mockk(relaxed = true))

        val res = impl.send("hello")
        assertTrue(res.isSuccess)
        assertEquals("hello", res.getOrNull())
        verify { okWs.send("hello") }
    }

    @Test
    fun `send text returns failure when underlying socket returns false`() {
        val okWs = mockk<WebSocket>(relaxed = true)
        every { factory.create(any(), any()) } returns Result.success(okWs)
        every { okWs.send("nope") } returns false

        val impl = StreamWebSocketImpl(logger, factory, subs)
        impl.open(mockk(relaxed = true))

        val res = impl.send("nope")
        assertTrue(res.isFailure)
        assertTrue(res.exceptionOrNull() is IOException)
        verify { okWs.send("nope") }
    }

    @Test
    fun `send text fails for empty input`() {
        val okWs = mockk<WebSocket>(relaxed = true)
        every { factory.create(any(), any()) } returns Result.success(okWs)

        val impl = StreamWebSocketImpl(logger, factory, subs)
        impl.open(mockk(relaxed = true))

        val res = impl.send("")
        assertTrue(res.isFailure)
        assertTrue(res.exceptionOrNull() is IllegalStateException)
    }

    // --- send(bytes) ---

    @Test
    fun `send bytes succeeds when underlying socket returns true`() {
        val okWs = mockk<WebSocket>(relaxed = true)
        every { factory.create(any(), any()) } returns Result.success(okWs)
        every { okWs.send(any<ByteString>()) } returns true

        val impl = StreamWebSocketImpl(logger, factory, subs)
        impl.open(mockk(relaxed = true))

        val payload = byteArrayOf(1, 2, 3)
        val res = impl.send(payload)
        assertTrue(res.isSuccess)
        assertTrue(res.getOrNull()!!.contentEquals(payload))
        verify { okWs.send(payload.toByteString(0, payload.size)) }
    }

    @Test
    fun `send bytes returns failure when underlying socket returns false`() {
        val okWs = mockk<WebSocket>(relaxed = true)
        every { factory.create(any(), any()) } returns Result.success(okWs)
        every { okWs.send(any<ByteString>()) } returns false

        val impl = StreamWebSocketImpl(logger, factory, subs)
        impl.open(mockk(relaxed = true))

        val payload = byteArrayOf(9, 9)
        val res = impl.send(payload)
        assertTrue(res.isFailure)
        assertTrue(res.exceptionOrNull() is IOException)
        verify { okWs.send(payload.toByteString(0, payload.size)) }
    }

    @Test
    fun `send bytes fails for empty input`() {
        val okWs = mockk<WebSocket>(relaxed = true)
        every { factory.create(any(), any()) } returns Result.success(okWs)

        val impl = StreamWebSocketImpl(logger, factory, subs)
        impl.open(mockk(relaxed = true))

        val res = impl.send(byteArrayOf())
        assertTrue(res.isFailure)
        assertTrue(res.exceptionOrNull() is IllegalStateException)
    }

    // --- close() ---

    @Test
    fun `close delegates to websocket with expected code and reason`() {
        val okWs = mockk<WebSocket>(relaxed = true)
        every { factory.create(any(), any()) } returns Result.success(okWs)
        every { okWs.close(any(), any()) } returns true

        val impl = StreamWebSocketImpl(logger, factory, subs)
        impl.open(mockk(relaxed = true))

        val res = impl.close()
        assertTrue(res.isSuccess)
        verify {
            okWs.close(SocketConstants.CLOSE_SOCKET_CODE, SocketConstants.CLOSE_SOCKET_REASON)
        }
    }

    // --- listener forwarding ---

    @Test
    fun `onOpen forwards to subscribers`() {
        val impl = StreamWebSocketImpl(logger, factory, subs)
        val listener = mockk<StreamWebSocketListener>(relaxed = true)

        every { subs.forEach(any()) } answers
            {
                val block = firstArg<(StreamWebSocketListener) -> Unit>()
                block(listener)
                Result.success(Unit)
            }

        val resp = mockResponse(101)
        impl.onOpen(mockk(relaxed = true), resp)
        verify { listener.onOpen(resp) }
    }

    @Test
    fun `onMessage text forwards to subscribers`() {
        val impl = StreamWebSocketImpl(logger, factory, subs)
        val listener = mockk<StreamWebSocketListener>(relaxed = true)

        every { subs.forEach(any()) } answers
            {
                val block = firstArg<(StreamWebSocketListener) -> Unit>()
                block(listener)
                Result.success(Unit)
            }

        impl.onMessage(mockk(relaxed = true), "ping")
        verify { listener.onMessage("ping") }
    }

    @Test
    fun `onMessage bytes forwards to subscribers`() {
        val impl = StreamWebSocketImpl(logger, factory, subs)
        val listener = mockk<StreamWebSocketListener>(relaxed = true)

        every { subs.forEach(any()) } answers
            {
                val block = firstArg<(StreamWebSocketListener) -> Unit>()
                block(listener)
                Result.success(Unit)
            }

        val bytes = "hi".toByteArray().toByteString()
        impl.onMessage(mockk(relaxed = true), bytes)
        verify { listener.onMessage(bytes) }
    }

    @Test
    fun `onFailure forwards to subscribers`() {
        val impl = StreamWebSocketImpl(logger, factory, subs)
        val listener = mockk<StreamWebSocketListener>(relaxed = true)

        every { subs.forEach(any()) } answers
            {
                val block = firstArg<(StreamWebSocketListener) -> Unit>()
                block(listener)
                Result.success(Unit)
            }

        val boom = IllegalStateException("nope")
        val resp: Response? = null
        impl.onFailure(mockk(relaxed = true), boom, resp)
        verify { listener.onFailure(boom, resp) }
    }

    @Test
    fun `onClosed forwards to subscribers`() {
        val impl = StreamWebSocketImpl(logger, factory, subs)
        val listener = mockk<StreamWebSocketListener>(relaxed = true)

        every { subs.forEach(any()) } answers
            {
                val block = firstArg<(StreamWebSocketListener) -> Unit>()
                block(listener)
                Result.success(Unit)
            }

        impl.onClosed(mockk(relaxed = true), 1000, "bye")
        verify { listener.onClosed(1000, "bye") }
    }

    @Test
    fun `onClosing forwards to subscribers`() {
        val impl = StreamWebSocketImpl(logger, factory, subs)
        val listener = mockk<StreamWebSocketListener>(relaxed = true)

        every { subs.forEach(any()) } answers
            {
                val block = firstArg<(StreamWebSocketListener) -> Unit>()
                block(listener)
                Result.success(Unit)
            }

        impl.onClosing(mockk(relaxed = true), 1001, "going away")
        verify { listener.onClosing(1001, "going away") }
    }

    // --- subscription manager delegation ---

    @Test
    fun `subscribe delegates to subscription manager`() {
        val impl = StreamWebSocketImpl(logger, factory, subs)
        val listener = mockk<StreamWebSocketListener>(relaxed = true)
        val sub = mockk<StreamSubscription>(relaxed = true)
        val opts =
            StreamSubscriptionManager.SubscribeOptions(
                retention =
                    StreamSubscriptionManager.SubscribeOptions.SubscriptionRetention
                        .KEEP_UNTIL_CANCELLED
            )
        every { subs.subscribe(listener, opts) } returns Result.success(sub)

        val res = impl.subscribe(listener, opts)
        assertTrue(res.isSuccess)
        assertSame(sub, res.getOrNull())
        verify { subs.subscribe(listener, opts) }
    }

    @Test
    fun `clear delegates to subscription manager`() {
        val impl = StreamWebSocketImpl(logger, factory, subs)
        every { subs.clear() } returns Result.success(Unit)

        val res = impl.clear()
        assertTrue(res.isSuccess)
        verify { subs.clear() }
        confirmVerified(subs)
    }

    @Test
    fun `forEach delegates to subscription manager`() {
        val impl = StreamWebSocketImpl(logger, factory, subs)
        val listener = mockk<StreamWebSocketListener>(relaxed = true)

        every { subs.forEach(any()) } answers
            {
                val block = firstArg<(StreamWebSocketListener) -> Unit>()
                block(listener) // execute the block to simulate delivery
                Result.success(Unit)
            }

        val ran = booleanArrayOf(false)
        val res =
            impl.forEach {
                ran[0] = true
                assertSame(listener, it)
            }
        assertTrue(res.isSuccess)
        assertTrue(ran[0])
        verify { subs.forEach(any()) }
    }

    // --- withSocket guard when not opened ---

    @Test
    fun `operations before open fail with IllegalStateException`() {
        val impl = StreamWebSocketImpl(logger, factory, subs)

        val sendText = impl.send("x")
        val sendBytes = impl.send(byteArrayOf(1))
        val closeRes = impl.close()

        assertTrue(sendText.isFailure && sendText.exceptionOrNull() is IllegalStateException)
        assertTrue(sendBytes.isFailure && sendBytes.exceptionOrNull() is IllegalStateException)
        assertTrue(closeRes.isFailure && closeRes.exceptionOrNull() is IllegalStateException)
    }
}
