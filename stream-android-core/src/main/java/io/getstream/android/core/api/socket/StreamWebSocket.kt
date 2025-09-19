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
package io.getstream.android.core.api.socket

import io.getstream.android.core.annotations.StreamInternalApi
import io.getstream.android.core.api.log.StreamLogger
import io.getstream.android.core.api.model.config.StreamSocketConfig
import io.getstream.android.core.api.socket.listeners.StreamWebSocketListener
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager
import io.getstream.android.core.internal.socket.SocketConstants.CLOSE_SOCKET_CODE
import io.getstream.android.core.internal.socket.SocketConstants.CLOSE_SOCKET_REASON
import io.getstream.android.core.internal.socket.StreamWebSocketImpl

/**
 * Represents a WebSocket connection used for real-time communication with the Stream API.
 *
 * Implementations of this interface manage the lifecycle of a socket connection, handle sending
 * messages, and provide subscription management for socket events.
 *
 * @param T The type of listener used to receive WebSocket events.
 */
@StreamInternalApi
public interface StreamWebSocket<T : StreamWebSocketListener> : StreamSubscriptionManager<T> {
    /**
     * Opens the WebSocket connection using the provided configuration.
     *
     * @param config The [StreamSocketConfig] defining connection parameters such as endpoint,
     *   authentication, and optional headers.
     * @return A [Result] indicating whether the connection was successfully initiated.
     */
    public fun open(config: StreamSocketConfig): Result<Unit>

    /**
     * Closes the WebSocket connection with a custom code and reason.
     *
     * @param code The closure status code
     * @param reason The reason message provided by the peer, if any.
     * @return A [Result] indicating whether the close operation was successful.
     */
    public fun close(
        code: Int = CLOSE_SOCKET_CODE,
        reason: String = CLOSE_SOCKET_REASON,
    ): Result<Unit>

    /**
     * Sends binary data through the WebSocket connection.
     *
     * @param data The raw bytes to be sent.
     * @return A [Result] containing the same [ByteArray] if successfully sent, or a failure if
     *   sending failed.
     */
    public fun send(data: ByteArray): Result<ByteArray>

    /**
     * Sends a text message through the WebSocket connection.
     *
     * @param text The UTF-8 encoded string to be sent.
     * @return A [Result] containing the same [String] if successfully sent, or a failure if sending
     *   failed.
     */
    public fun send(text: String): Result<String>
}

/**
 * Creates a new [StreamWebSocket] instance.
 *
 * @param logger The [StreamLogger] to use for logging.
 * @param socketFactory The [StreamWebSocketFactory] to use for creating WebSocket connections.
 * @param subscriptionManager The [StreamSubscriptionManager] to use for managing subscriptions.
 * @return A new [StreamWebSocket] instance.
 */
@StreamInternalApi
public fun <T : StreamWebSocketListener> StreamWebSocket(
    logger: StreamLogger,
    socketFactory: StreamWebSocketFactory,
    subscriptionManager: StreamSubscriptionManager<T>,
): StreamWebSocket<T> =
    StreamWebSocketImpl(
        logger = logger,
        socketFactory = socketFactory,
        subscriptionManager = subscriptionManager,
    )
