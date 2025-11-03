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

package io.getstream.android.core.api.socket.listeners

import io.getstream.android.core.annotations.StreamInternalApi
import io.getstream.android.core.api.model.connection.StreamConnectionState
import io.getstream.android.core.api.model.connection.network.StreamNetworkState
import kotlin.test.assertEquals
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okio.ByteString.Companion.encodeUtf8
import org.junit.Test

internal class StreamListenersDefaultImplsTest {

    @Test
    fun `StreamClientListener default methods are no-ops`() {
        // Given
        val listener = object : StreamClientListener {}

        // When
        listener.onState(StreamConnectionState.Idle)
        listener.onEvent("ignored")
        listener.onError(IllegalStateException("boom"))

        // Then: no exceptions thrown
    }

    @Test
    fun `StreamClientListener overrides get invoked`() = runTest {
        // Given
        val stateChannel = Channel<StreamConnectionState>(capacity = 1)
        val eventChannel = Channel<Any>(capacity = 1)
        val errorChannel = Channel<Throwable>(capacity = 1)
        val networkChannel = Channel<StreamNetworkState>(capacity = 1)

        val listener =
            object : StreamClientListener {
                override fun onState(state: StreamConnectionState) {
                    stateChannel.trySend(state)
                }

                override fun onEvent(event: Any) {
                    eventChannel.trySend(event)
                }

                override fun onError(err: Throwable) {
                    errorChannel.trySend(err)
                }

                override fun onNetworkState(state: StreamNetworkState) {
                    networkChannel.trySend(state)
                }
            }

        val state = StreamConnectionState.Connecting.Opening("user")
        val event = "event"
        val error = IllegalArgumentException("fail")

        listener.onState(state)
        listener.onEvent(event)
        listener.onError(error)

        assertEquals(state, stateChannel.receive())
        assertEquals(event, eventChannel.receive())
        assertEquals(error, errorChannel.receive())
    }

    @Test
    fun `StreamWebSocketListener default methods are no-ops`() {
        // Given
        val listener = object : StreamWebSocketListener {}
        val response =
            Response.Builder()
                .request(Request.Builder().url("https://example.com").build())
                .protocol(Protocol.HTTP_1_1)
                .code(101)
                .message("Switching Protocols")
                .build()

        // When
        listener.onOpen(response)
        listener.onMessage("message")
        listener.onMessage("bytes".encodeUtf8())
        listener.onFailure(IllegalStateException("boom"), response)
        listener.onClosed(1000, "closed")
        listener.onClosing(1001, "closing")

        // Then: no exceptions thrown
    }

    @Test
    fun `StreamWebSocketListener overrides get invoked`() = runTest {
        // Given
        val events = Channel<String>(capacity = 6)
        val response =
            Response.Builder()
                .request(Request.Builder().url("https://example.com").build())
                .protocol(Protocol.HTTP_1_1)
                .code(101)
                .message("Switching Protocols")
                .build()
        val error = IllegalArgumentException("fail")

        val listener =
            object : StreamWebSocketListener {
                override fun onOpen(response: Response) {
                    launch { events.send("open:${response.code}") }
                }

                override fun onMessage(bytes: okio.ByteString) {
                    launch { events.send("bytes:${bytes.utf8()}") }
                }

                override fun onMessage(text: String) {
                    launch { events.send("text:$text") }
                }

                override fun onFailure(t: Throwable, response: Response?) {
                    launch { events.send("failure:${t.message}") }
                }

                override fun onClosed(code: Int, reason: String) {
                    launch { events.send("closed:$code:$reason") }
                }

                override fun onClosing(code: Int, reason: String) {
                    launch { events.send("closing:$code:$reason") }
                }
            }

        // When
        listener.onOpen(response)
        listener.onMessage("text")
        listener.onMessage("bytes".encodeUtf8())
        listener.onFailure(error, response)
        listener.onClosed(1000, "bye")
        listener.onClosing(1001, "closing")

        // Then
        assertEquals("open:101", events.receive())
        assertEquals("text:text", events.receive())
        assertEquals("bytes:bytes", events.receive())
        assertEquals("failure:fail", events.receive())
        assertEquals("closed:1000:bye", events.receive())
        assertEquals("closing:1001:closing", events.receive())
    }
}
