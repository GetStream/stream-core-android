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

package io.getstream.android.core.api.http

import io.getstream.android.core.annotations.StreamInternalApi
import io.getstream.android.core.api.authentication.StreamTokenManager
import io.getstream.android.core.api.model.value.StreamApiKey
import io.getstream.android.core.api.model.value.StreamHttpClientInfoHeader
import io.getstream.android.core.api.serialization.StreamJsonSerialization
import io.getstream.android.core.api.socket.StreamConnectionIdHolder
import io.getstream.android.core.internal.http.interceptor.StreamApiKeyInterceptor
import io.getstream.android.core.internal.http.interceptor.StreamAuthInterceptor
import io.getstream.android.core.internal.http.interceptor.StreamClientInfoInterceptor
import io.getstream.android.core.internal.http.interceptor.StreamConnectionIdInterceptor
import io.getstream.android.core.internal.http.interceptor.StreamEndpointErrorInterceptor
import okhttp3.Interceptor

/**
 * Provides a set of OkHttp interceptors for use with the Stream SDKs.
 *
 * @see [StreamAuthInterceptor]
 * @see [StreamConnectionIdInterceptor]
 * @see [StreamApiKeyInterceptor]
 */
@StreamInternalApi
public object StreamOkHttpInterceptors {
    /**
     * Creates an OkHttp interceptor that adds authentication headers and retries on token errors.
     *
     * @param authType The value set on the `stream-auth-type` header (e.g., "jwt").
     * @param tokenManager Provides, invalidates, and refreshes tokens.
     * @param jsonParser JSON parser used to decode error payloads when requests fail.
     * @return An OkHttp interceptor.
     */
    public fun auth(
        authType: String,
        tokenManager: StreamTokenManager,
        jsonParser: StreamJsonSerialization,
    ): Interceptor = StreamAuthInterceptor(tokenManager, jsonParser, authType)

    /**
     * Creates an OkHttp interceptor that adds a `connection_id` query parameter to the request URL.
     *
     * @param connectionIdHolder The holder that provides the connection ID.
     * @return An OkHttp interceptor.
     */
    public fun connectionId(connectionIdHolder: StreamConnectionIdHolder): Interceptor =
        StreamConnectionIdInterceptor(connectionIdHolder)

    /**
     * Creates an OkHttp interceptor that adds the client info header to the request.
     *
     * @param clientInfoHeader The client info header to add.
     * @return An OkHttp interceptor.
     */
    public fun clientInfo(clientInfoHeader: StreamHttpClientInfoHeader): Interceptor =
        StreamClientInfoInterceptor(clientInfoHeader)

    /**
     * Creates an OkHttp interceptor that adds the Stream API key to the request as a query
     * parameter.
     *
     * @param apiKey The API key to add.
     * @return An OkHttp interceptor.
     */
    public fun apiKey(apiKey: StreamApiKey): Interceptor = StreamApiKeyInterceptor(apiKey)

    /**
     * Creates an OkHttp interceptor that parses and throws
     * [io.getstream.android.core.api.model.exceptions.StreamEndpointException] for error responses.
     *
     * @param jsonParser JSON parser used to decode error payloads when requests fail.
     * @return An OkHttp interceptor.
     */
    public fun error(jsonParser: StreamJsonSerialization): Interceptor =
        StreamEndpointErrorInterceptor(jsonParser)
}
