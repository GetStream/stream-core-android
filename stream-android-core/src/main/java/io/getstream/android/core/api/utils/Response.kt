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
package io.getstream.android.core.api.utils

import io.getstream.android.core.api.model.exceptions.StreamEndpointErrorData
import io.getstream.android.core.api.serialization.StreamJsonSerialization
import okhttp3.Response

/**
 * Extracts the API error from the response body.
 *
 * @param with The JSON parser to use for parsing the response body.
 * @return The API error, or a failure if the response body could not be parsed.
 * @receiver The response to extract the error from.
 */
public fun Response.toErrorData(with: StreamJsonSerialization): Result<StreamEndpointErrorData> =
    runCatching { peekBody(Long.MAX_VALUE).string() }
        .flatMap { with.fromJson(it, StreamEndpointErrorData::class.java) }
