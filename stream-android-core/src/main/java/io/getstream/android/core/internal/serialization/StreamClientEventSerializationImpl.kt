package io.getstream.android.core.internal.serialization

import io.getstream.android.core.api.model.event.StreamClientWsEvent
import io.getstream.android.core.api.serialization.StreamClientEventSerialization
import io.getstream.android.core.api.serialization.StreamJsonSerialization

internal class StreamClientEventSerializationImpl(private val jsonParser: StreamJsonSerialization) :
    StreamClientEventSerialization {
    override fun serialize(data: StreamClientWsEvent): Result<String> = jsonParser.toJson(data)

    override fun deserialize(raw: String): Result<StreamClientWsEvent> =
        jsonParser.fromJson(raw, StreamClientWsEvent::class.java)
}