/*
 * Copyright (c) 2014-2026 Stream.io Inc. All rights reserved.
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

package io.getstream.android.core.internal.serialization.moshi

import com.squareup.moshi.JsonDataException
import io.getstream.android.core.api.model.connection.StreamConnectedUser
import io.getstream.android.core.api.model.event.StreamClientWsEvent
import io.getstream.android.core.internal.model.events.StreamClientConnectedEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Test

internal class StreamCoreMoshiProviderDateParsingTest {

    private val moshi = StreamCoreMoshiProvider().builder {}.build()

    @Test
    fun `connection ok with RFC3339 date strings parses`() {
        // Given: a connection.ok frame as sent by the video coordinator (captured from a real
        // handshake) — all date fields are RFC3339 strings, not epoch millis.
        val adapter = moshi.adapter(StreamClientWsEvent::class.java)
        val raw =
            """
            {"type":"connection.ok","created_at":"2026-07-06T07:47:26.660610521Z",
            "connection_id":"6a397f9d-0a1d-27b1-0200-000002b8ca27",
            "me":{"id":"2T16C16M","name":"2T16C16M","image":"","custom":{"email":"2T16C16M"},
            "language":"","role":"user","teams":[],
            "created_at":"2026-07-06T07:47:26.592958Z",
            "updated_at":"2026-07-06T07:47:26.656029Z",
            "banned":false,"online":true,
            "last_active":"2026-07-06T07:47:26.592958Z",
            "devices":[],"invisible":false,"mutes":[],"channel_mutes":[],
            "unread_count":0,"total_unread_count":0,"unread_channels":0,"unread_threads":0}}
            """
                .trimIndent()

        // When
        val event = adapter.fromJson(raw)

        // Then
        assertTrue(event is StreamClientConnectedEvent)
        assertEquals("6a397f9d-0a1d-27b1-0200-000002b8ca27", event.connectionId)
        assertEquals("2T16C16M", event.me.id)
        assertEquals(utcDate("2026-07-06T07:47:26.592"), event.me.createdAt)
        assertEquals(utcDate("2026-07-06T07:47:26.656"), event.me.updatedAt)
        assertEquals(utcDate("2026-07-06T07:47:26.592"), event.me.lastActive)
    }

    @Test
    fun `connection ok with epoch millis dates still parses`() {
        // Given: the pre-existing wire format — dates as epoch millis.
        val adapter = moshi.adapter(StreamClientWsEvent::class.java)
        val millis = 1_783_064_846_592L
        val raw =
            """
            {"type":"connection.ok","connection_id":"conn-1",
            "me":{"id":"u1","language":"en","role":"user","teams":[],
            "created_at":$millis,"updated_at":$millis}}
            """
                .trimIndent()

        // When
        val event = adapter.fromJson(raw)

        // Then
        assertTrue(event is StreamClientConnectedEvent)
        assertEquals(Date(millis), event.me.createdAt)
        assertEquals(Date(millis), event.me.updatedAt)
    }

    @Test
    fun `dates serialize as epoch millis`() {
        // Given
        val adapter = moshi.adapter(StreamConnectedUser::class.java)
        val user =
            StreamConnectedUser(
                createdAt = Date(1000),
                id = "u",
                language = "en",
                role = "user",
                updatedAt = Date(2000),
                teams = emptyList(),
            )

        // When
        val json = adapter.toJson(user)

        // Then: outbound wire format is unchanged.
        assertTrue(json.contains("\"created_at\":1000"))
        assertTrue(json.contains("\"updated_at\":2000"))
    }

    @Test
    fun `RFC3339 date round-trips through millis`() {
        // Given
        val adapter = moshi.adapter(Date::class.java)
        val parsed = adapter.fromJson("\"2026-07-06T07:47:26.592Z\"")

        // When
        val json = adapter.toJson(parsed)
        val reparsed = adapter.fromJson(json)

        // Then
        assertNotNull(parsed)
        assertEquals(parsed, reparsed)
    }

    @Test(expected = JsonDataException::class)
    fun `unsupported date token throws`() {
        // Given
        val adapter = moshi.adapter(Date::class.java)

        // When
        adapter.fromJson("true")

        // Then expect exception
    }

    private fun utcDate(isoMillisPrecision: String): Date =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .parse(isoMillisPrecision)!!
}
