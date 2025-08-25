package io.getstream.android.core.internal.serialization

import com.squareup.moshi.JsonReader
import io.getstream.android.core.api.model.event.StreamClientWsEvent
import io.getstream.android.core.api.serialization.StreamClientEventSerialization
import io.getstream.android.core.api.serialization.StreamProductEventSerialization
import io.getstream.android.core.api.utils.runCatchingCancellable
import okio.Buffer

/**
 * Represents a composite event that can be either a [StreamClientWsEvent] or a product-specific event.
 *
 * @param T The type of the product-specific event.
 */
internal class StreamCompositeSerializationEvent<T> private constructor(
    val core: StreamClientWsEvent? = null,
    val product: T? = null,
) {
    companion object {
        /**
         * Creates a new [StreamCompositeSerializationEvent] with the given [event].
         *
         * @param event The event to wrap.
         * @return A new [StreamCompositeSerializationEvent] with the given [event].
         */
        fun <T> internal(event: StreamClientWsEvent) = StreamCompositeSerializationEvent<T>(core = event)

        /**
         * Creates a new [StreamCompositeSerializationEvent] with the given [event].
         *
         * @param event The event to wrap.
         * @return A new [StreamCompositeSerializationEvent] with the given [event].
         */
        fun <T> external(event: T) = StreamCompositeSerializationEvent(product = event)

        /**
         * Creates a new [StreamCompositeSerializationEvent] with the given [event].
         *
         * @param event The event to wrap.
         * @return A new [StreamCompositeSerializationEvent] with the given [event].
         */
        fun <T> both(event: StreamClientWsEvent, product: T) =
            StreamCompositeSerializationEvent(core = event, product = product)
    }
}

/**
 * Serializes and deserializes [StreamCompositeSerializationEvent] objects.
 *
 * @param T The type of the product-specific event.
 */
internal class StreamCompositeEventSerializationImpl<T>(
    private val internal: StreamClientEventSerialization,
    private val external: StreamProductEventSerialization<T>,
    private val internalTypes: Set<String> = setOf("connection.ok", "connection.error", "health.check"),
    private val alsoExternal: Set<String> = emptySet(),
) {
    /**
     * Serializes the given [data] into a [String].
     *
     * @param data The data to serialize.
     * @return `Result.success(String)` when encoding succeeds, or `Result.failure(Throwable)` when
     *   the process fails.
     */
    fun serialize(data: StreamCompositeSerializationEvent<T>): Result<String> {
        data.core?.let { return internal.serialize(it) }
        data.product?.let { return external.serialize(it) }
        return Result.failure(NullPointerException())
    }

    /**
     * Deserializes the given [raw] string into a [StreamCompositeSerializationEvent].
     *
     * @param raw The string to deserialize.
     * @return `Result.success(StreamCompositeSerializationEvent)` when decoding succeeds, or
     *   `Result.failure(Throwable)` when the process fails.
     */
    fun deserialize(raw: String): Result<StreamCompositeSerializationEvent<T>> =
        runCatchingCancellable {
            val type = peekType(raw)
            return try {
                when (type) {
                    null -> {
                        val ext = external
                        ext.deserialize(raw).map { StreamCompositeSerializationEvent.external(it) }
                    }
                    in alsoExternal -> {
                        val coreSer = internal
                        val extSer  = external
                        val core = coreSer.deserialize(raw).getOrThrow()
                        val prod = extSer.deserialize(raw).getOrThrow()
                        Result.success(StreamCompositeSerializationEvent.both(core, prod))
                    }
                    in internalTypes -> {
                        val coreSer = internal
                        coreSer.deserialize(raw).map { StreamCompositeSerializationEvent.internal(it) }
                    }
                    else -> {
                        val ext = external
                        ext.deserialize(raw).map { StreamCompositeSerializationEvent.external(it) }
                    }
                }
            } catch (e: Throwable) {
                Result.failure(e)
            }
        }

    private fun peekType(raw: String): String? {
        val reader = JsonReader.of(Buffer().writeUtf8(raw))
        reader.isLenient = true
        return try {
            if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) return null
            reader.beginObject()
            var result: String? = null
            while (reader.hasNext()) {
                val name = reader.nextName()
                if (name == "type" && reader.peek() == JsonReader.Token.STRING) {
                    result = reader.nextString()
                    // consume the rest to keep reader state valid
                    while (reader.hasNext()) reader.skipValue()
                    break
                } else {
                    reader.skipValue()
                }
            }
            if (reader.peek() == JsonReader.Token.END_OBJECT) reader.endObject()
            result
        } catch (_: Throwable) {
            null
        }
    }
}