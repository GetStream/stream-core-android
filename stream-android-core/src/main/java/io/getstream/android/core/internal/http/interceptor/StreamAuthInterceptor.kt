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

import io.getstream.android.core.api.authentication.StreamTokenManager
import io.getstream.android.core.api.model.exceptions.StreamEndpointErrorData
import io.getstream.android.core.api.model.exceptions.StreamEndpointException
import io.getstream.android.core.api.serialization.StreamJsonSerialization
import io.getstream.android.core.api.utils.toErrorData
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * An OkHttp [Interceptor] that attaches Stream auth headers and performs a single retry with a
 * refreshed token when the server indicates the token is invalid/expired.
 *
 * Behavior:
 * 1. Loads a token (blocking) from [StreamTokenManager] and adds:
 *     - `stream-auth-type: <authType>`
 *     - `Authorization: <token>`
 * 2. Executes the request.
 * 3. If the response is unsuccessful, it attempts to parse the body as [StreamEndpointErrorData].
 *     - If the error code is a token error (e.g., 401/403), it invalidates and refreshes the token
 *       and retries **once** with the new token.
 *     - Otherwise, it throws a [StreamEndpointException] carrying the parsed error (if available).
 *
 * Notes:
 * - OkHttp interceptors are synchronous; token loading/refresh uses [runBlocking] by design.
 * - Error body peeking is capped to avoid unbounded memory use.
 *
 * @param tokenManager Provides, invalidates, and refreshes tokens.
 * @param jsonParser JSON parser used to decode error payloads when requests fail.
 * @param authType The value set on the `stream-auth-type` header (e.g., "jwt").
 */
internal class StreamAuthInterceptor(
    private val tokenManager: StreamTokenManager,
    private val jsonParser: StreamJsonSerialization,
    private val authType: String,
) : Interceptor {
    private companion object {
        const val HEADER_STREAM_AUTH_TYPE = "stream-auth-type"
        const val HEADER_AUTHORIZATION = "Authorization"

        // never sent, only used internally
        const val HEADER_RETRIED_ON_AUTH = "x-stream-retried-on-auth"
        const val PEEK_ERROR_BYTES_MAX = 1_000_000L // 1 MB
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val token =
            runBlocking { tokenManager.loadIfAbsent() }
                .getOrEndpointException("Failed to load token.")
        val original = chain.request()
        val authed = original.withAuthHeaders(authType, token.rawValue)

        val first = chain.proceed(authed)
        if (first.isSuccessful) {
            return first
        }

        // Peek only; do NOT consume
        val errorData = first.toErrorData(jsonParser)

        val alreadyRetried = original.header(HEADER_RETRIED_ON_AUTH) == "present"

        if (errorData.isSuccess) {
            val error = errorData.getOrEndpointException("Failed to parse error body.")

            // Only handle token errors here
            if (isTokenInvalidErrorCode(error.code) && !alreadyRetried) {
                // refresh & retry once
                first.close()
                tokenManager.invalidate().getOrEndpointException("Failed to invalidate token")
                val refreshed =
                    runBlocking { tokenManager.refresh() }
                        .getOrEndpointException("Failed to refresh token")

                val retried =
                    original
                        .withAuthHeaders(authType, refreshed.rawValue)
                        .newBuilder()
                        .header(HEADER_RETRIED_ON_AUTH, "present")
                        .build()

                return chain.proceed(retried) // pass result (ok or error) downstream
            }

            // Non-token error, or token error but we already retried:
            // pass the original failed response downstream; DO NOT throw here.
            return first
        } else {
            // Unknown/invalid error body → pass through
            return first
        }
    }

    private fun Request.withAuthHeaders(authType: String, bearer: String): Request =
        newBuilder()
            .addHeader(HEADER_STREAM_AUTH_TYPE, authType)
            .addHeader(HEADER_AUTHORIZATION, bearer)
            .build()

    fun isTokenInvalidErrorCode(code: Int): Boolean = code == 40 || code == 41 || code == 42

    private fun <T> Result<T>.getOrEndpointException(message: String = ""): T = getOrElse {
        throw StreamEndpointException(message, null, it)
    }
}
