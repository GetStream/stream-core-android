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

package io.getstream.android.core.internal.http.interceptor

import io.getstream.android.core.api.socket.StreamConnectionIdHolder
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * OkHttp [Interceptor] that appends a `connection_id` query parameter to the request URL.
 *
 * Behavior:
 * - Adds the connection ID query parameter if it's not already present and the connection ID is
 *   non-blank.
 *
 * @param connectionIdHolder The holder that provides the connection ID.
 * @see [StreamConnectionIdHolder]
 */
internal class StreamConnectionIdInterceptor(
    private val connectionIdHolder: StreamConnectionIdHolder
) : Interceptor {
    private companion object {
        private const val QUERY_PARAM_CONNECTION_ID = "connection_id"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val id = connectionIdHolder.getConnectionId().getOrNull()
        val trimmedId = id?.trim()

        // Skip if blank or already present
        if (
            trimmedId.isNullOrBlank() ||
                request.url.queryParameter(QUERY_PARAM_CONNECTION_ID) != null
        ) {
            return chain.proceed(request)
        }

        val newRequest = request.withConnectionId(trimmedId)
        return chain.proceed(newRequest)
    }

    private fun Request.withConnectionId(id: String): Request {
        val urlWithConnectionId =
            url.newBuilder().addQueryParameter(QUERY_PARAM_CONNECTION_ID, id).build()

        return newBuilder().url(urlWithConnectionId).build()
    }
}
