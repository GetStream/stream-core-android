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

/**
 * Generic, bidirectional (de-)serialization contract.
 *
 * The interface is intentionally generic, so you can:
 * * **Serialize** an arbitrary “raw” value — for example a Kotlin data object, a JSON tree, or a
 *   protocol-buffer message — into an intermediate representation (`SerializedOut`) such as a JSON
 *   string, a byte buffer, or a database row.
 * * **Deserialize** a value that originated outside your process (`DeserializeIn`) back into a
 *   higher-level domain object (`DeserializeOut`).
 *
 * The type parameters allow you to model asymmetric pipelines where:
 * * the value you serialize is *not* the same type you later deserialize, and
 * * the outbound / inbound formats differ.
 *
 * All operations return a [Result] so callers can safely propagate failures (e.g. malformed input,
 * I/O errors, schema mismatches) without throwing.
 *
 * @param RawIn The type accepted by [serialize] (the “raw” input you want to encode).
 * @param SerializedOut The type produced by [serialize] (the encoded representation).
 * @param DeserializeIn The type accepted by [deserialize] (usually data read from the wire / disk).
 * @param DeserializeOut The type produced by [deserialize] (the fully parsed domain value).
 */
@StreamCoreApi
interface StreamGenericSerialization<
    in RawIn,
    out SerializedOut,
    in DeserializeIn,
    out DeserializeOut,
> {

    /**
     * Encodes [data] into a transport- or storage-friendly form.
     *
     * @param data The value to serialize.
     * @return `Result.success(SerializedOut)` when encoding succeeds, or
     *   `Result.failure(Throwable)` when the process fails.
     */
    fun serialize(data: RawIn): Result<SerializedOut>

    /**
     * Decodes [raw] into a higher-level object.
     *
     * @param raw The serialized input to decode (e.g. a JSON string or byte array).
     * @return `Result.success(DeserializeOut)` on success, or `Result.failure(Throwable)` if
     *   decoding fails.
     */
    fun deserialize(raw: DeserializeIn): Result<DeserializeOut>
}
