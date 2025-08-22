package io.getstream.android.core.api.model.config

import io.getstream.android.core.annotations.StreamCoreApi
import io.getstream.android.core.api.serialization.StreamClientEventSerialization
import io.getstream.android.core.api.serialization.StreamJsonSerialization

/**
 * Configuration for serialization and deserialization in the Stream client.
 *
 * @param json The JSON serialization implementation.
 * @param eventParser The event parsing implementation.
 */
@StreamCoreApi
@ConsistentCopyVisibility
data class StreamClientSerializationConfig private constructor(
    val json: StreamJsonSerialization? = null,
    val eventParser: StreamClientEventSerialization? = null,
) {
    companion object {

        /**
         * Creates a default [StreamClientSerializationConfig]. Using the internal implementations.
         *
         * @return A default [StreamClientSerializationConfig].
         */
        fun defaults() = StreamClientSerializationConfig()

        /**
         * Creates a [StreamClientSerializationConfig] with the given JSON serialization.
         *
         * @param serialization The JSON serialization implementation.
         * @return A [StreamClientSerializationConfig] with the given JSON serialization.
         */
        fun json(serialization: StreamJsonSerialization) = StreamClientSerializationConfig(json = serialization)

        /**
         * Creates a [StreamClientSerializationConfig] with the given event parsing.
         *
         * @param serialization The event parsing implementation.
         * @return A [StreamClientSerializationConfig] with the given event parsing.
         */
        fun event(serialization: StreamClientEventSerialization) = StreamClientSerializationConfig(eventParser = serialization)
    }
}
