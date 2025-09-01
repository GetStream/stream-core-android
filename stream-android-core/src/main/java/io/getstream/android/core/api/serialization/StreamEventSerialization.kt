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
import io.getstream.android.core.api.model.event.StreamClientWsEvent
import io.getstream.android.core.internal.serialization.StreamClientEventSerializationImpl

/**
 * Interface for serializing and deserializing product event objects.
 *
 * All operations return a [Result] so callers can safely propagate failures (e.g. malformed input,
 * I/O errors, schema mismatches) without throwing.
 *
 * @param T The product event type handled by this interface.
 */
@StreamInternalApi
public interface StreamEventSerialization<T> {

    /**
     * Encodes a product event into a [String] suitable for transport or storage.
     *
     * @param data The event to serialize.
     * @return `Result.success(String)` when encoding succeeds, or `Result.failure(Throwable)` when
     *   the process fails.
     */
    public fun serialize(data: T): Result<String>

    /**
     * Decodes a product event from a [String] representation.
     *
     * @param raw The string to deserialize.
     * @return `Result.success(T)` when decoding succeeds, or `Result.failure(Throwable)` when the
     *   process fails.
     */
    public fun deserialize(raw: String): Result<T>
}

/**
 * Creates a new [StreamEventSerialization] instance that can serialize
 * [io.getstream.android.core.api.model.event.StreamClientWsEvent] objects.
 *
 * @param jsonParser The [StreamJsonSerialization] to use for JSON serialization and
 *   deserialization.
 * @return A new [StreamEventSerialization] instance.
 */
@StreamInternalApi
public fun StreamEventSerialization(
    jsonParser: StreamJsonSerialization
): StreamEventSerialization<StreamClientWsEvent> = StreamClientEventSerializationImpl(jsonParser)
