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
package io.getstream.android.core.api.socket.listeners

import io.getstream.android.core.annotations.StreamCoreApi
import io.getstream.android.core.api.subscribe.StreamSubscription
import okhttp3.Response
import okio.ByteString

/**
 * Listener interface for receiving WebSocket events from the Stream API.
 *
 * This interface provides callback hooks for the lifecycle of a WebSocket connection including
 * creation, message delivery, failures, and closure events. It extends [StreamSubscription] so that
 * listeners can be registered/unregistered consistently with other subscription-based APIs in
 * Stream Core.
 *
 * ### Usage
 * Implement this interface to handle WebSocket events in your client:
 * ```kotlin
 * val listener = object : StreamWebSocketListener {
 *     override fun onOpen(response: Response) {
 *         println("Socket opened: ${response.message}")
 *     }
 *
 *     override fun onMessage(text: String) {
 *         println("Received message: $text")
 *     }
 *
 *     override fun onFailure(t: Throwable, response: Response?) {
 *         println("Socket failure: ${t.message}")
 *     }
 * }
 * ```
 *
 * @see StreamSubscription For lifecycle management of subscriptions.
 * @see okhttp3.WebSocketListener For the underlying OkHttp implementation.
 */
@StreamCoreApi
interface StreamWebSocketListener {
    /**
     * Called when the socket connection is established.
     *
     * @param response The handshake response returned by the server.
     */
    fun onOpen(response: Response) {}

    /**
     * Called when a new binary message is received from the server.
     *
     * @param bytes The raw binary payload received.
     */
    fun onMessage(bytes: ByteString) {}

    /**
     * Called when a new text message is received from the server.
     *
     * @param text The UTF-8 encoded text payload received.
     */
    fun onMessage(text: String) {}

    /**
     * Called when an error occurs on the socket.
     *
     * @param t The throwable cause of the failure.
     * @param response The optional server response associated with the error.
     */
    fun onFailure(t: Throwable, response: Response?) {}

    /**
     * Called when the socket connection has been closed.
     *
     * @param code The closure status code as defined by RFC 6455.
     * @param reason The reason message provided by the peer, if any.
     */
    fun onClosed(code: Int, reason: String) {}

    /**
     * Called when the socket is about to close.
     *
     * @param code The closure status code as defined by RFC 6455.
     * @param reason The reason message provided by the peer, if any.
     */
    fun onClosing(code: Int, reason: String) {}
}
