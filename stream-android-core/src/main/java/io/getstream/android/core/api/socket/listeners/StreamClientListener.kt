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
import io.getstream.android.core.api.model.connection.StreamConnectionState
import io.getstream.android.core.api.model.event.StreamClientWsEvent

/**
 * Listener interface for Feeds socket events.
 *
 * This interface defines methods to handle socket state changes and events. Implement this
 * interface to receive updates about the socket connection state and incoming events.
 */
@StreamCoreApi
interface StreamClientListener {
    /**
     * Called when the socket connection state changes.
     *
     * @param state The new state of the WebSocket connection.
     */
    fun onState(state: StreamConnectionState) {}

    /**
     * Called when a new event is received from the socket.
     *
     * @param event The event received from the WebSocket.
     */
    fun onEvent(event: Any) {}

    /**
     * Called when an error occurs on the client.
     *
     * @param err The error that occurred.
     */
    fun onError(err: Throwable) {}
}
