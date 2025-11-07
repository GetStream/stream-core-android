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
import io.getstream.android.core.api.socket.StreamWebSocketFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Factory for creating WebSocket connections.
 *
 * @param okHttpClient The OkHttpClient instance to use for creating WebSocket connections.
 */
internal class StreamWebSocketFactoryImpl(
    private val okHttpClient: OkHttpClient = OkHttpClient(),
    private val logger: StreamLogger,
) : StreamWebSocketFactory {
    override fun create(
        streamSocketConfig: StreamSocketConfig,
        listener: WebSocketListener,
    ): Result<WebSocket> = runCatching {
        logger.v { "[createSocket] config: $streamSocketConfig" }
        val url =
            "${streamSocketConfig.url}?" +
                "api_key=${streamSocketConfig.apiKey.rawValue}" +
                "&stream-auth-type=${streamSocketConfig.authType}" +
                "&X-Stream-Client=${streamSocketConfig.clientInfoHeader.rawValue}"
        val request =
            Request.Builder()
                .url(url)
                .addHeader("Connection", "Upgrade")
                .addHeader("Upgrade", "websocket")
                .addHeader("X-Stream-Client", streamSocketConfig.clientInfoHeader.rawValue)
                .build()
        okHttpClient.newWebSocket(request, listener)
    }
}
