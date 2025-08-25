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
package io.getstream.android.core.api.model.connection

import io.getstream.android.core.annotations.StreamCoreApi

@StreamCoreApi
sealed class StreamConnectionState {
    /** The client is not connected and not trying to connect. Initial state for fresh objects. */
    data object Idle : StreamConnectionState()

    /** The client was connected and is now disconnected. */
    data class Disconnected(val cause: Throwable? = null) : StreamConnectionState()

    /**
     * The client is connected and authenticated.
     *
     * @property connectedUser The user that is connected to the client.
     * @property connectionId The connection ID.
     */
    data class Connected(val connectedUser: StreamConnectedUser, val connectionId: String) :
        StreamConnectionState()

    /** The client is trying to connect. */
    sealed class Connecting : StreamConnectionState() {
        /**
         * Opening a new connection
         *
         * @property userId The user ID that is being connected.
         */
        data class Opening(val userId: String) : Connecting()

        /**
         * Authenticating a new connection. Socket is open, but not authenticated.
         *
         * @property userId The user ID that is being connected.
         */
        data class Authenticating(val userId: String) : Connecting()
    }
}
