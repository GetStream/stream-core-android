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
import io.getstream.android.core.api.serialization.StreamJsonSerialization
import io.getstream.android.core.api.utils.toErrorData
import kotlin.fold
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Interceptor that handles API errors by parsing the response body and throwing a
 * [StreamEndpointException].
 *
 * @param jsonParser The JSON parser to parse the error response.
 */
internal class StreamEndpointErrorInterceptor(private val jsonParser: StreamJsonSerialization) :
    Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())

        if (!response.isSuccessful) {
            // Try to parse a Stream API error from the response body
            response.toErrorData(jsonParser).fold(
                    onSuccess = { apiError ->
                        throw StreamEndpointException(
                            "Failed request: ${chain.request().url}",
                            apiError,
                        )
                    },
                    onFailure = { error ->
                        throw StreamEndpointException(
                            message = "Failed request: ${chain.request().url}",
                            cause = error,
                        )
                    },
                )
        }
        return response
    }
}
