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
package io.getstream.android.core.internal.serialization.moshi

import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import io.getstream.android.core.api.model.event.StreamClientWsEvent
import io.getstream.android.core.internal.model.events.StreamHealthCheckEvent
import java.util.Date
import org.junit.Assert.*
import org.junit.Test

class MoshiProviderTest {

    private fun moshi(): Moshi = StreamCoreMoshiProvider().builder { /* no-op */ }.build()

    // --- DateMillisAdapter ---

    @Test
    fun `DateMillisAdapter toJson writes epoch millis`() {
        val m = moshi()
        val adapter = m.adapter(Date::class.java)

        val date = Date(1_704_000_123_456L)
        val json = adapter.toJson(date)

        assertEquals("1704000123456", json)
        // round-trip
        val parsed = adapter.fromJson(json)
        assertEquals(date.time, parsed!!.time)
    }

    @Test
    fun `DateMillisAdapter fromJson reads epoch millis`() {
        val m = moshi()
        val adapter = m.adapter(Date::class.java)

        val parsed = adapter.fromJson("123456789")
        assertNotNull(parsed)
        assertEquals(123456789L, parsed!!.time)
    }

    // --- builder(configure) hook ---

    data class Custom(val v: String)

    object CustomAdapter {
        @com.squareup.moshi.ToJson fun toJson(c: Custom): Map<String, String> = mapOf("v" to c.v)

        @com.squareup.moshi.FromJson
        fun fromJson(map: Map<String, String>): Custom = Custom(map.getValue("v"))
    }

    @Test
    fun `builder passes through configuration lambda (custom adapter works)`() {
        val m = StreamCoreMoshiProvider().builder { it.add(CustomAdapter) }.build()
        val a = m.adapter(Custom::class.java)

        val json = a.toJson(Custom("ok"))
        assertEquals("""{"v":"ok"}""", json)

        val parsed = a.fromJson("""{"v":"hello"}""")
        assertEquals(Custom("hello"), parsed)
    }

    // --- Polymorphic adapter for StreamClientWsEvent ---

    @Test
    fun `polymorphic deserialization resolves health_check`() {
        val m = moshi()
        val base = m.adapter(StreamClientWsEvent::class.java)
        val json =
            """
                    {
                    "connection_id": "abc123",
                    "created_at": 1234567789,
                    "custom": {},
                    "type": "health.check"
                    }
                    """
                .trimIndent()
        // Minimal payload with the discriminator only
        val event = base.fromJson(json)
        assertTrue("Should be StreamHealthCheckEvent", event is StreamHealthCheckEvent)
    }

    @Test(expected = JsonDataException::class)
    fun `polymorphic throws on unknown type`() {
        val m = moshi()
        val base = m.adapter(StreamClientWsEvent::class.java)
        base.fromJson("""{"type":"unknown.type"}""") // should throw
    }

    @Test
    fun `polymorphic toJson adds discriminator for health_check`() {
        val m = moshi()
        val base = m.adapter(StreamClientWsEvent::class.java)

        // If StreamHealthCheckEvent is a Kotlin object, we can round-trip it directly.
        val json =
            base.toJson(
                StreamHealthCheckEvent(
                    connectionId = "cid",
                    createdAt = Date(0),
                    custom = emptyMap(),
                    type = "health.check",
                )
            )
        // The exact shape may include only the discriminator; assert it’s present and correct.
        assertTrue(json.contains(""""type":"health.check""""))
        // And it should deserialize back to the same subtype.
        val parsed = base.fromJson(json)
        assertTrue(parsed is StreamHealthCheckEvent)
    }

    @Test
    fun `toJson returns millis for non-null Date`() {
        val date = Date(1734567890000L)
        val millis = StreamCoreMoshiProvider.DateMillisAdapter.toJson(date)
        assertEquals(1734567890000L, millis)
    }

    @Test
    fun `toJson returns null for null Date`() {
        val millis = StreamCoreMoshiProvider.DateMillisAdapter.toJson(null)
        assertNull(millis)
    }

    @Test
    fun `fromJson returns Date for non-null millis`() {
        val millis = 1734567890000L
        val date = StreamCoreMoshiProvider.DateMillisAdapter.fromJson(millis)
        assertEquals(Date(millis), date)
    }

    @Test
    fun `fromJson returns null for null millis`() {
        val date = StreamCoreMoshiProvider.DateMillisAdapter.fromJson(null)
        assertNull(date)
    }
}
