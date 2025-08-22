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
package io.getstream.android.core.api.serialization

import io.getstream.android.core.annotations.StreamCoreApi
import io.getstream.android.core.api.model.event.StreamClientWsEvent
import io.getstream.android.core.internal.serialization.StreamClientEventSerializationImpl

/**
 * Interface for serializing and deserializing [StreamClientWsEvent] objects.
 *
 * All operations return a [Result] so callers can safely propagate failures (e.g. malformed input,
 * I/O errors, schema mismatches) without throwing.
 *
 * @see StreamClientWsEvent for the event type handled by this interface.
 */
@StreamCoreApi
interface StreamClientEventSerialization {
    /**
     * Encodes a [StreamClientWsEvent] into a [String] suitable for transport or storage.
     *
     * @param data The event to serialize.
     * @return `Result.success(String)` when encoding succeeds, or
     *   `Result.failure(Throwable)` when the process fails.
     */
    fun serialize(data: StreamClientWsEvent): Result<String>

    /**
     * Decodes a [String] into a [StreamClientWsEvent].
     *
     * @param raw The serialized input to decode (e.g. a JSON string).
     * @return `Result.success(StreamClientWsEvent)` on success, or `Result.failure(Throwable)` if
     *   decoding fails.
     */
    fun deserialize(raw: String): Result<StreamClientWsEvent>
}

/**
 * Creates a new [StreamClientEventSerialization] instance.
 *
 * @param jsonParser The [StreamJsonSerialization] to use for JSON serialization and deserialization.
 * @return A new [StreamClientEventSerialization] instance.
 */
@StreamCoreApi
fun StreamClientEventSerialization(
    jsonParser: StreamJsonSerialization,
): StreamClientEventSerialization = StreamClientEventSerializationImpl(jsonParser)
