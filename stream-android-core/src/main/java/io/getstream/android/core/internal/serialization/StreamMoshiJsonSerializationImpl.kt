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

package io.getstream.android.core.internal.serialization

import com.squareup.moshi.Moshi
import io.getstream.android.core.api.serialization.StreamJsonSerialization

internal class StreamMoshiJsonSerializationImpl(private val moshi: Moshi) :
    StreamJsonSerialization {
    override fun toJson(any: Any): Result<String> = runCatching {
        moshi.adapter(any.javaClass).toJson(any)
    }

    override fun <T : Any> fromJson(raw: String, clazz: Class<T>): Result<T> = runCatching {
        val fromJson = moshi.adapter(clazz).fromJson(raw)
        if (fromJson == null) {
            throw IllegalArgumentException("Failed to parse $clazz from raw string: $raw")
        }
        fromJson
    }
}
