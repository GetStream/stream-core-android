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
package io.getstream.android.core.api.model.config

import io.getstream.android.core.annotations.StreamCoreApi
import io.getstream.android.core.api.model.value.StreamApiKey
import io.getstream.android.core.api.model.value.StreamHttpClientInfoHeader

/**
 * Configuration for the Stream socket.
 *
 * @param url The URL to connect to.
 * @param apiKey The API key for authentication.
 * @param authType The type of authentication used (e.g., "jwt").
 * @param clientInfoHeader The client info header.
 */
@StreamCoreApi
@ConsistentCopyVisibility
data class StreamSocketConfig
private constructor(
    val url: String,
    val apiKey: StreamApiKey,
    val authType: String,
    val clientInfoHeader: StreamHttpClientInfoHeader,
) {
    companion object {
        private const val JWT_AUTH_TYPE = "jwt"
        private const val ANONYMOUS_AUTH_TYPE = "anonymous"

        /**
         * Creates a JWT-based [StreamSocketConfig].
         *
         * @param url The URL to connect to.
         * @param apiKey The API key for authentication.
         * @param clientInfoHeader The client info header.
         * @return A JWT-based [StreamSocketConfig].
         */
        fun jwt(
            url: String,
            apiKey: StreamApiKey,
            clientInfoHeader: StreamHttpClientInfoHeader,
        ): StreamSocketConfig {
            require(url.isNotBlank()) { "URL must not be blank" }
            return StreamSocketConfig(url, apiKey, JWT_AUTH_TYPE, clientInfoHeader)
        }

        /**
         * Creates an anonymous [StreamSocketConfig].
         *
         * @param url The URL to connect to.
         * @param apiKey The API key for authentication.
         * @param clientInfoHeader The client info header.
         * @return An anonymous [StreamSocketConfig].
         */
        fun anonymous(
            url: String,
            apiKey: StreamApiKey,
            clientInfoHeader: StreamHttpClientInfoHeader,
        ): StreamSocketConfig {
            require(url.isNotBlank()) { "URL must not be blank" }
            return StreamSocketConfig(url, apiKey, ANONYMOUS_AUTH_TYPE, clientInfoHeader)
        }

        /**
         * Creates a custom [StreamSocketConfig].
         *
         * @param url The URL to connect to.
         * @param apiKey The API key for authentication.
         * @param authType The type of authentication used (e.g., "jwt").
         * @param clientInfoHeader The client info header.
         * @return A custom [StreamSocketConfig].
         */
        fun custom(
            url: String,
            apiKey: StreamApiKey,
            authType: String,
            clientInfoHeader: StreamHttpClientInfoHeader,
        ): StreamSocketConfig {
            require(url.isNotBlank()) { "URL must not be blank" }
            require(authType.isNotBlank()) { "Auth type must not be blank" }
            return StreamSocketConfig(url, apiKey, authType, clientInfoHeader)
        }
    }
}
