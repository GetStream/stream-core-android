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
package io.getstream.android.core.internal.serialization

import io.getstream.android.core.api.model.event.StreamClientWsEvent
import io.getstream.android.core.api.serialization.StreamClientEventSerialization
import io.getstream.android.core.api.serialization.StreamProductEventSerialization
import io.mockk.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class StreamCompositeEventSerializationImplTest {

    private lateinit var internalSer: StreamClientEventSerialization
    private lateinit var externalSer: StreamProductEventSerialization<String>
    private lateinit var coreEvent: StreamClientWsEvent

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        internalSer = mockk(relaxed = true)
        externalSer = mockk(relaxed = true)
        coreEvent = mockk(relaxed = true)
    }

    private fun newSut(
        internalTypes: Set<String> = setOf("connection.ok", "connection.error", "health.check"),
        alsoExternal: Set<String> = emptySet(),
    ) =
        StreamCompositeEventSerializationImpl(
            internal = internalSer,
            external = externalSer,
            internalTypes = internalTypes,
            alsoExternal = alsoExternal,
        )

    // --- serialize() ---

    @Test
    fun `serialize - core delegates to internal`() {
        val sut = newSut()
        every { internalSer.serialize(coreEvent) } returns Result.success("CORE_JSON")

        val res = sut.serialize(StreamCompositeSerializationEvent.internal<String>(coreEvent))

        assertTrue(res.isSuccess)
        assertEquals("CORE_JSON", res.getOrNull())
        verify(exactly = 1) { internalSer.serialize(coreEvent) }
        verify(exactly = 0) { externalSer.serialize(any()) }
    }

    @Test
    fun `serialize - product delegates to external`() {
        val sut = newSut()
        every { externalSer.serialize("P") } returns Result.success("PROD_JSON")

        val res = sut.serialize(StreamCompositeSerializationEvent.external("P"))

        assertTrue(res.isSuccess)
        assertEquals("PROD_JSON", res.getOrNull())
        verify(exactly = 1) { externalSer.serialize("P") }
        verify(exactly = 0) { internalSer.serialize(any()) }
    }

    @Test
    fun `serialize - neither core nor product returns failure`() {
        // Create an instance with both nulls via reflection (private ctor).
        val k = StreamCompositeSerializationEvent::class.java.declaredConstructors.first()
        k.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val emptyEvt = k.newInstance(null, null, null) as StreamCompositeSerializationEvent<String>

        val sut = newSut()
        val res = sut.serialize(emptyEvt)

        assertTrue(res.isFailure)
        assertTrue(res.exceptionOrNull() is NullPointerException)
        verify { internalSer wasNot Called }
        verify { externalSer wasNot Called }
    }

    // --- deserialize() routing ---

    @Test
    fun `deserialize - null type (non-object) routes to external`() {
        val sut = newSut()
        every { externalSer.deserialize("[]") } returns Result.success("X")

        val res = sut.deserialize("[]").getOrThrow()

        assertNull(res.core)
        assertEquals("X", res.product)
        verify(exactly = 1) { externalSer.deserialize("[]") }
        verify(exactly = 0) { internalSer.deserialize(any()) }
    }

    @Test
    fun `deserialize - null type (object without type) routes to external`() {
        val sut = newSut()
        every { externalSer.deserialize("{}") } returns Result.success("Y")

        val res = sut.deserialize("{}").getOrThrow()

        assertNull(res.core)
        assertEquals("Y", res.product)
        verify(exactly = 1) { externalSer.deserialize("{}") }
        verify(exactly = 0) { internalSer.deserialize(any()) }
    }

    @Test
    fun `deserialize - non-string type value routes to external`() {
        val sut = newSut()
        every { externalSer.deserialize("""{"type":123}""") } returns Result.success("Z")

        val res = sut.deserialize("""{"type":123}""").getOrThrow()

        assertNull(res.core)
        assertEquals("Z", res.product)
        verify(exactly = 1) { externalSer.deserialize("""{"type":123}""") }
        verify(exactly = 0) { internalSer.deserialize(any()) }
    }

    @Test
    fun `deserialize - internalTypes hit routes to internal`() {
        val sut = newSut(internalTypes = setOf("connection.ok"))
        every { internalSer.deserialize("""{"type":"connection.ok"}""") } returns
            Result.success(coreEvent)

        val res = sut.deserialize("""{"type":"connection.ok"}""").getOrThrow()

        assertEquals(coreEvent, res.core)
        assertNull(res.product)
        verify(exactly = 1) { internalSer.deserialize(any()) }
        verify(exactly = 0) { externalSer.deserialize(any()) }
    }

    @Test
    fun `deserialize - alsoExternal hit routes to both (success on both)`() {
        val sut = newSut(internalTypes = setOf("dual"), alsoExternal = setOf("dual"))
        every { internalSer.deserialize("""{"type":"dual"}""") } returns Result.success(coreEvent)
        every { externalSer.deserialize("""{"type":"dual"}""") } returns Result.success("P")

        val res = sut.deserialize("""{"type":"dual"}""").getOrThrow()

        assertEquals(coreEvent, res.core)
        assertEquals("P", res.product)
        verify(exactly = 1) { internalSer.deserialize(any()) }
        verify(exactly = 1) { externalSer.deserialize(any()) }
    }

    @Test
    fun `deserialize - other type routes to external`() {
        val sut = newSut(internalTypes = setOf("a", "b"))
        every { externalSer.deserialize("""{"type":"c"}""") } returns Result.success("C")

        val res = sut.deserialize("""{"type":"c"}""").getOrThrow()

        assertNull(res.core)
        assertEquals("C", res.product)
        verify(exactly = 1) { externalSer.deserialize(any()) }
        verify(exactly = 0) { internalSer.deserialize(any()) }
    }

    // --- deserialize() failures propagate correctly ---

    @Test
    fun `deserialize - external failure when type is null propagates failure`() {
        val sut = newSut()
        every { externalSer.deserialize("[]") } returns
            Result.failure(IllegalStateException("ext fail"))

        val res = sut.deserialize("[]")

        assertTrue(res.isFailure)
        assertEquals("ext fail", res.exceptionOrNull()!!.message)
        verify(exactly = 1) { externalSer.deserialize("[]") }
        verify(exactly = 0) { internalSer.deserialize(any()) }
    }

    @Test
    fun `deserialize - internal failure when type is internal propagates failure`() {
        val sut = newSut(internalTypes = setOf("connection.error"))
        every { internalSer.deserialize("""{"type":"connection.error"}""") } returns
            Result.failure(IllegalArgumentException("int fail"))

        val res = sut.deserialize("""{"type":"connection.error"}""")

        assertTrue(res.isFailure)
        assertEquals("int fail", res.exceptionOrNull()!!.message)
        verify(exactly = 1) { internalSer.deserialize(any()) }
        verify(exactly = 0) { externalSer.deserialize(any()) }
    }

    @Test
    fun `deserialize - alsoExternal branch internal failure throws, whole call fails (ext not called)`() {
        val sut = newSut(internalTypes = setOf("dual"), alsoExternal = setOf("dual"))
        every { internalSer.deserialize("""{"type":"dual"}""") } returns
            Result.failure(RuntimeException("core boom"))

        val res = sut.deserialize("""{"type":"dual"}""")

        assertTrue(res.isFailure)
        assertEquals("core boom", res.exceptionOrNull()!!.message)
        verify(exactly = 1) { internalSer.deserialize(any()) }
        // ext is NOT called because core getOrThrow() already threw
        verify(exactly = 0) { externalSer.deserialize(any()) }
    }

    @Test
    fun `deserialize - alsoExternal branch external failure throws, whole call fails`() {
        val sut = newSut(internalTypes = setOf("dual"), alsoExternal = setOf("dual"))
        every { internalSer.deserialize("""{"type":"dual"}""") } returns Result.success(coreEvent)
        every { externalSer.deserialize("""{"type":"dual"}""") } returns
            Result.failure(IllegalStateException("ext boom"))

        val res = sut.deserialize("""{"type":"dual"}""")

        assertTrue(res.isFailure)
        assertEquals("ext boom", res.exceptionOrNull()!!.message)
        verify(exactly = 1) { internalSer.deserialize(any()) }
        verify(exactly = 1) { externalSer.deserialize(any()) }
    }

    @Test
    fun `deserialize - type amid other fields triggers consume-rest loop and routes internal`() {
        // type not first; extra fields after it -> exercises both skipValue branches in peekType
        val sut = newSut(internalTypes = setOf("connection.ok"))
        val json = """{"a":1,"type":"connection.ok","b":[1,{"x":2}],"c":{"d":"e"}}"""
        every { internalSer.deserialize(json) } returns Result.success(coreEvent)

        val res = sut.deserialize(json).getOrThrow()

        assertEquals(coreEvent, res.core)
        assertNull(res.product)
        verify(exactly = 1) { internalSer.deserialize(json) }
        verify(exactly = 0) { externalSer.deserialize(any()) }
    }

    @Test
    fun `deserialize - unknown type with trailing fields routes external (consume-rest loop still hit)`() {
        // type is a String but NOT in internalTypes; there are trailing fields to consume
        val sut = newSut(internalTypes = setOf("connection.ok"))
        val json = """{"a":1,"type":"unknown","tail":{"x":1,"y":[2,3]}}"""
        every { externalSer.deserialize(json) } returns Result.success("UNK")

        val res = sut.deserialize(json).getOrThrow()

        assertNull(res.core)
        assertEquals("UNK", res.product)
        verify(exactly = 1) { externalSer.deserialize(json) }
        verify(exactly = 0) { internalSer.deserialize(any()) }
    }

    @Test
    fun `deserialize - malformed json triggers peekType catch path and routes external`() {
        // Missing closing brace -> JsonReader throws -> peekType catches -> returns null
        val sut = newSut()
        val malformed = """{"type":"connection.ok" """ // intentionally invalid JSON
        every { externalSer.deserialize(malformed) } returns Result.success("OK")

        val res = sut.deserialize(malformed).getOrThrow()

        assertNull(res.core)
        assertEquals("OK", res.product)
        verify(exactly = 1) { externalSer.deserialize(malformed) }
        verify(exactly = 0) { internalSer.deserialize(any()) }
    }

    @Test
    fun `deserialize - switching internalTypes changes routing`() {
        val json = """{"type":"connection.ok"}"""

        // stubs for both paths
        every { internalSer.deserialize(json) } returns Result.success(coreEvent)
        every { externalSer.deserialize(json) } returns Result.success("EXT")

        // when "connection.ok" is considered internal -> goes to internal
        val sutInternal = newSut(internalTypes = setOf("connection.ok"))
        val r1 = sutInternal.deserialize(json).getOrThrow()
        assertEquals(coreEvent, r1.core)
        assertNull(r1.product)

        // when it's NOT considered internal anymore -> goes to external
        val sutExternal = newSut(internalTypes = emptySet())
        val r2 = sutExternal.deserialize(json).getOrThrow()
        assertNull(r2.core)
        assertEquals("EXT", r2.product)

        // verify calls went to the expected serializers exactly once each overall
        verify(exactly = 1) { internalSer.deserialize(json) }
        verify(exactly = 1) { externalSer.deserialize(json) }
    }
}
