package io.getstream.android.core.internal.serialization

import io.getstream.android.core.api.model.event.StreamClientWsEvent
import io.getstream.android.core.api.serialization.StreamJsonSerialization
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert
import org.junit.Test

class StreamClientEventSerializationImplTest {

    private val jsonSerialization: StreamJsonSerialization = mockk()

    private val parser = StreamClientEventSerializationImpl(jsonSerialization)

    @Test
    fun `serialize delegates to jsonParser success`() {
        val event = mockk<StreamClientWsEvent>()
        every { jsonSerialization.toJson(event) } returns Result.success("""{"type":"ok"}""")

        val result = parser.serialize(event)

        Assert.assertTrue(result.isSuccess)
        Assert.assertEquals("""{"type":"ok"}""", result.getOrNull())
    }

    @Test
    fun `serialize delegates to jsonParser failure`() {
        val event = mockk<StreamClientWsEvent>()
        val exception = RuntimeException("boom")
        every { jsonSerialization.toJson(event) } returns Result.failure(exception)

        val result = parser.serialize(event)

        Assert.assertTrue(result.isFailure)
        Assert.assertSame(exception, result.exceptionOrNull())
    }

    @Test
    fun `deserialize delegates to jsonParser success`() {
        val json = """{"type":"connection.ok"}"""
        val event = mockk<StreamClientWsEvent>()
        every { jsonSerialization.fromJson(json, StreamClientWsEvent::class.java) } returns
            Result.success(event)

        val result = parser.deserialize(json)

        Assert.assertTrue(result.isSuccess)
        Assert.assertSame(event, result.getOrNull())
    }

    @Test
    fun `deserialize delegates to jsonParser failure`() {
        val json = """{"type":"bad"}"""
        val exception = IllegalArgumentException("bad input")
        every { jsonSerialization.fromJson(json, StreamClientWsEvent::class.java) } returns
            Result.failure(exception)

        val result = parser.deserialize(json)

        Assert.assertTrue(result.isFailure)
        Assert.assertSame(exception, result.exceptionOrNull())
    }
}