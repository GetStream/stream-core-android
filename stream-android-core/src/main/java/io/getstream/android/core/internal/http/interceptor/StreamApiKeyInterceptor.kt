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

import io.getstream.android.core.api.model.exceptions.StreamEndpointException
import io.getstream.android.core.api.model.value.StreamApiKey
import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp interceptor that appends the Stream API key to the request as a query parameter.
 *
 * Behavior:
 * - If the URL already contains `api_key`, the request is left unchanged.
 * - If [apiKey] is blank, the request is left unchanged (alternatively: throw).
 *
 * Note:
 * - Using a query parameter exposes the key in logs and caches. If you prefer not to, consider
 *   adding it as a header instead (e.g. "X-Stream-Api-Key").
 */
internal class StreamApiKeyInterceptor(private val apiKey: StreamApiKey) : Interceptor {
    private companion object {
        private const val API_KEY_PARAM = "api_key"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url

        if (apiKey.rawValue.isBlank()) {
            // StreamApiKey is self enforced via the fromString, but still we are defensive here
            throw StreamEndpointException("API key must not be blank!")
        }

        val urlWithApiKey =
            url.newBuilder().addQueryParameter(API_KEY_PARAM, apiKey.rawValue).build()

        val newRequest = request.newBuilder().url(urlWithApiKey).build()

        return chain.proceed(newRequest)
    }
}
