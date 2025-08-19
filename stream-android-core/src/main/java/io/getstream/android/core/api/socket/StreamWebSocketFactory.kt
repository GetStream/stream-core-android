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
package io.getstream.android.core.api.socket

import io.getstream.android.core.annotations.StreamCoreApi
import io.getstream.android.core.api.model.config.StreamSocketConfig
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Factory interface for creating [StreamWebSocket] instances.
 *
 * Implementations define how WebSocket connections are established and configured within the Stream
 * SDK. This allows both SDK internals and consumers to plug in custom WebSocket creation logic
 * while preserving a unified contract.
 *
 * The created socket will be configured with the given [StreamSocketConfig], and bound to the
 * provided [WebSocketListener] for low-level socket events.
 */
@StreamCoreApi
interface StreamWebSocketFactory {

    /**
     * Creates a new [WebSocket] instance.
     *
     * @param streamSocketConfig Configuration for the WebSocket connection, including endpoint URL,
     *   headers, and connection parameters.
     * @param listener An OkHttp [WebSocketListener] to receive raw WebSocket lifecycle events such
     *   as open, message, closing, and failure.
     * @return A [Result] wrapping the created [WebSocket]. On success, the returned [WebSocket]
     *   will be connected and bound to [listener].
     */
    fun create(
        streamSocketConfig: StreamSocketConfig,
        listener: WebSocketListener,
    ): Result<WebSocket>
}
