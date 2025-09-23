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
package io.getstream.android.core.internal.socket

import io.getstream.android.core.annotations.StreamDsl
import io.getstream.android.core.api.log.StreamLogger
import io.getstream.android.core.api.model.config.StreamSocketConfig
import io.getstream.android.core.api.socket.StreamWebSocket
import io.getstream.android.core.api.socket.StreamWebSocketFactory
import io.getstream.android.core.api.socket.listeners.StreamWebSocketListener
import io.getstream.android.core.api.subscribe.StreamSubscription
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager
import java.io.IOException
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString

internal open class StreamWebSocketImpl<T : StreamWebSocketListener>(
    private val logger: StreamLogger,
    private val socketFactory: StreamWebSocketFactory,
    private val subscriptionManager: StreamSubscriptionManager<T>,
) : WebSocketListener(), StreamWebSocket<T> {
    private lateinit var socket: WebSocket

    override fun open(config: StreamSocketConfig): Result<Unit> = runCatching {
        socket =
            socketFactory
                .create(config, this)
                .onFailure {
                    logger.e { "[open] SocketFactory failed to create socket. ${it.message}" }
                }
                .getOrThrow()
    }

    override fun close(code: Int, reason: String): Result<Unit> = withSocket {
        logger.d { "[close#withReason] Closing socket. Code: $code, Reason: $reason" }
        socket.close(code, reason)
    }

    override fun send(data: ByteArray): Result<ByteArray> = withSocket {
        logger.v { "[send] Sending data: $data" }
        if (data.isNotEmpty()) {
            val result = socket.send(data.toByteString(0, data.size))
            if (!result) {
                val message = "[send] socket.send() returned false"
                logger.e { message }
                throw IOException(message)
            }
            data
        } else {
            logger.e { "[send] Empty data!" }
            throw IllegalStateException("Empty raw data!")
        }
    }

    override fun send(text: String): Result<String> = withSocket {
        logger.v { "[send] Sending text: $text" }
        if (text.isNotEmpty()) {
            val result = socket.send(text)
            if (!result) {
                val message = "[send] socket.send() returned false"
                logger.e { message }
                throw IOException(message)
            }
            text
        } else {
            logger.e { "[send] Empty data!" }
            throw IllegalStateException("Empty raw data!")
        }
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        logger.d { "[onOpen] Socket is open" }
        forEach { it.onOpen(response) }
        super.onOpen(webSocket, response)
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        logger.v { "[onMessage] Socket message: $bytes" }
        forEach { it.onMessage(bytes) }
        super.onMessage(webSocket, bytes)
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        logger.v { "[onMessage] Socket message: $text" }
        forEach { it.onMessage(text) }
        super.onMessage(webSocket, text)
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        logger.e(t) { "[onFailure] Socket failure" }
        forEach { it.onFailure(t, response) }
        super.onFailure(webSocket, t, response)
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        logger.d { "[onClosed] Socket closed. Code: $code, Reason: $reason" }
        forEach { it.onClosed(code, reason) }
        super.onClosed(webSocket, code, reason)
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        logger.d { "[onClosing] Socket closing. Code: $code, Reason: $reason" }
        forEach { it.onClosing(code, reason) }
        super.onClosing(webSocket, code, reason)
    }

    override fun subscribe(
        listener: T,
        options: StreamSubscriptionManager.Options,
    ): Result<StreamSubscription> = subscriptionManager.subscribe(listener, options)

    override fun clear(): Result<Unit> = subscriptionManager.clear()

    override fun forEach(block: (T) -> Unit): Result<Unit> = subscriptionManager.forEach(block)

    @StreamDsl
    private inline fun <V> withSocket(block: (WebSocket) -> V) = runCatching {
        if (::socket.isInitialized) {
            block(socket)
        } else {
            val message = "[withSocket] The socket is not initialized. Call `open()` first."
            logger.e { message }
            throw IllegalStateException(message)
        }
    }
}
