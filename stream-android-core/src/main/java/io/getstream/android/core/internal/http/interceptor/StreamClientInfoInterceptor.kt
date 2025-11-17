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

import io.getstream.android.core.api.model.value.StreamHttpClientInfoHeader
import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp interceptor that appends the `X-Stream-Client` header to the request.
 *
 * Behavior:
 * - Adds the header if it's not already present.
 *
 * @param clientInfo The client info header value.
 */
internal class StreamClientInfoInterceptor(private val clientInfo: StreamHttpClientInfoHeader) :
    Interceptor {
    private companion object {
        private const val HEADER_X_STREAM_CLIENT = "X-Stream-Client"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val requestWithHeaders =
            request.newBuilder().addHeader(HEADER_X_STREAM_CLIENT, clientInfo.rawValue).build()
        return chain.proceed(requestWithHeaders)
    }
}
