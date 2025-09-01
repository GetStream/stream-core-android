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
package io.getstream.android.core.api.serialization

import io.getstream.android.core.annotations.StreamInternalApi
import io.getstream.android.core.annotations.StreamPublishedApi

/**
 * General contract for JSON serialization and deserialization.
 *
 * This interface abstracts away the actual JSON parsing engine (e.g. Moshi, Gson,
 * kotlinx.serialization), allowing the SDK to plug in a specific implementation without exposing
 * the underlying library.
 *
 * Implementations are expected to provide robust error handling and return results wrapped in
 * [Result].
 */
@StreamInternalApi
public interface StreamJsonSerialization {
    /**
     * Converts an object into its JSON string representation.
     *
     * @param any The object to serialize.
     * @return A [Result] containing the JSON string if successful, or a failure with the underlying
     *   exception if serialization fails.
     */
    public fun toJson(any: Any): Result<String>

    /**
     * Converts a JSON string into an object of the specified class.
     *
     * @param raw The JSON string to deserialize.
     * @param clazz The [Class] of type [T] that the JSON should be converted into.
     * @return A [Result] containing the deserialized object if successful, or a failure with the
     *   underlying exception if deserialization fails.
     */
    public fun <T : Any> fromJson(raw: String, clazz: Class<T>): Result<T>
}

/**
 * Convenience extension function to deserialize a JSON string into an object of the specified type.
 *
 * @param T The type of the object to deserialize.
 * @return A [Result] containing the deserialized object if successful, or a failure with the
 *   underlying exception if deserialization fails.
 * @see [StreamJsonSerialization.fromJson]
 */
@StreamInternalApi
@JvmSynthetic
public inline fun <reified T : Any> StreamJsonSerialization.fromJson(raw: String): Result<T> =
    fromJson(raw, T::class.java)
