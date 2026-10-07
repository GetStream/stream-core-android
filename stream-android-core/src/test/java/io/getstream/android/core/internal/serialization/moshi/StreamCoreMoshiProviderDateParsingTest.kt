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
    fun `connection ok with epoch nanos dates parses`() {
        // Given: a connection.ok frame as sent by the v2 gateway used by Feeds (captured from a
        // real handshake) — all date fields are epoch nanoseconds.
        val adapter = moshi.adapter(StreamClientWsEvent::class.java)
        val raw =
            """
            {"type":"connection.ok","created_at":1790947725196662581,
            "connection_id":"6abb765b-0a82-0bb6-0300-000000179c31",
            "me":{"id":"u1","name":"U1","image":"","custom":{},"language":"","role":"user",
            "teams":[],"created_at":1755586996702859000,"updated_at":1765800663371302000,
            "banned":false,"online":true,"last_active":1790947725188417871}}
            """
                .trimIndent()

        // When
        val event = adapter.fromJson(raw)

        // Then
        assertTrue(event is StreamClientConnectedEvent)
        assertEquals(utcDate("2025-08-19T07:03:16.702"), event.me.createdAt)
        assertEquals(utcDate("2025-12-15T12:11:03.371"), event.me.updatedAt)
        assertEquals(utcDate("2026-10-02T13:28:45.188"), event.me.lastActive)
    }

    @Test
    fun `zero epoch nanos parses as the epoch`() {
        // Given: the v2 gateway writes zero and pre-1970 times as 0.
        val adapter = moshi.adapter(Date::class.java)

        // When
        val date = adapter.fromJson("0")

        // Then
        assertEquals(Date(0), date)
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

        // Then: outbound wire format is unchanged (millis, even though numeric reads are nanos).
        assertTrue(json.contains("\"created_at\":1000"))
        assertTrue(json.contains("\"updated_at\":2000"))
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
