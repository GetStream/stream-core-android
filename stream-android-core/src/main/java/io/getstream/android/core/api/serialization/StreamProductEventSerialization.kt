package io.getstream.android.core.api.serialization

import io.getstream.android.core.annotations.StreamCoreApi

/**
 * Interface for serializing and deserializing product event objects.
 *
 * All operations return a [Result] so callers can safely propagate failures (e.g. malformed input,
 * I/O errors, schema mismatches) without throwing.
 *
 * @param T The product event type handled by this interface.
 */
@StreamCoreApi
interface StreamProductEventSerialization<T> {

    /**
     * Encodes a product event into a [String] suitable for transport or storage.
     *
     * @param data The event to serialize.
     * @return `Result.success(String)` when encoding succeeds, or `Result.failure(Throwable)` when
     *   the process fails.
     */
    fun serialize(data: T): Result<String>

    /**
     * Decodes a product event from a [String] representation.
     *
     * @param raw The string to deserialize.
     * @return `Result.success(T)` when decoding succeeds, or `Result.failure(Throwable)` when
     *   the process fails.
     */
    fun deserialize(raw: String): Result<T>
}
