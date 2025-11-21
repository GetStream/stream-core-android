package io.getstream.android.core.api.model.config

import io.getstream.android.core.api.model.event.StreamClientWsEvent
import io.getstream.android.core.api.serialization.StreamEventSerialization
import io.getstream.android.core.api.serialization.StreamJsonSerialization
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class StreamClientSerializationConfigTest {

    @Test
    fun `json builder injects json implementation`() {
        val json = FakeJsonSerialization()
        val productEvents = FakeEventSerialization<Any>()
        val alsoExternal = setOf("custom:event")

        val config = StreamClientSerializationConfig.json(json, productEvents, alsoExternal)

        assertSame(json, config.json)
        assertNull(config.eventParser)
        assertSame(productEvents, config.productEventSerializers)
        assertEquals(alsoExternal, config.alsoExternal)
    }

    @Test
    fun `event builder injects event parser`() {
        val eventParser = FakeEventSerialization<StreamClientWsEvent>()
        val productEvents = FakeEventSerialization<Any>()
        val alsoExternal = setOf("product:event")

        val config = StreamClientSerializationConfig.event(eventParser, productEvents, alsoExternal)

        assertSame(eventParser, config.eventParser)
        assertNull(config.json)
        assertSame(productEvents, config.productEventSerializers)
        assertEquals(alsoExternal, config.alsoExternal)
    }

    private class FakeJsonSerialization : StreamJsonSerialization {
        override fun toJson(any: Any): Result<String> = Result.success("{}")

        override fun <T : Any> fromJson(raw: String, clazz: Class<T>): Result<T> =
            Result.failure(UnsupportedOperationException())
    }

    private class FakeEventSerialization<T> : StreamEventSerialization<T> {
        override fun serialize(data: T): Result<String> = Result.success("event")

        override fun deserialize(raw: String): Result<T> =
            Result.failure(UnsupportedOperationException())
    }
}
