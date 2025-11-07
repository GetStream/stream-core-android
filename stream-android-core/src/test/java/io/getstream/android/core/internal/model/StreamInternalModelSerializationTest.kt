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

package io.getstream.android.core.internal.model

import com.squareup.moshi.JsonDataException
import io.getstream.android.core.api.model.connection.StreamConnectedUser
import io.getstream.android.core.internal.model.authentication.StreamWSAuthMessageRequest
import io.getstream.android.core.internal.model.events.EVENT_TYPE_CONNECTION_OK
import io.getstream.android.core.internal.model.events.StreamClientConnectedEvent
import io.getstream.android.core.internal.model.events.StreamClientConnectionErrorEvent
import io.getstream.android.core.internal.model.events.StreamHealthCheckEvent
import io.getstream.android.core.internal.serialization.moshi.StreamCoreMoshiProvider
import java.util.Date
import kotlin.test.assertEquals
import org.junit.Test

internal class StreamInternalModelSerializationTest {

    private val moshi = StreamCoreMoshiProvider().builder {}.build()

    @Test(expected = JsonDataException::class)
    fun `StreamWSAuthMessageRequest missing required fields throws`() {
        // Given
        val adapter = moshi.adapter(StreamWSAuthMessageRequest::class.java)
        val invalidJson = "{\"products\":null,\"token\":null}"

        // When
        adapter.fromJson(invalidJson)

        // Then expect exception
    }

    @Test(expected = JsonDataException::class)
    fun `StreamClientConnectedEvent missing connectionId throws`() {
        // Given
        val adapter = moshi.adapter(StreamClientConnectedEvent::class.java)
        val user =
            StreamConnectedUser(
                createdAt = Date(0),
                id = "u",
                language = "en",
                role = "user",
                updatedAt = Date(0),
                teams = emptyList(),
            )
        val jsonWithNullId =
            "{" +
                "\"me\":${moshi.adapter(StreamConnectedUser::class.java).toJson(user)}," +
                "\"type\":\"${EVENT_TYPE_CONNECTION_OK}\"" +
                "}"

        // When
        adapter.fromJson(jsonWithNullId)

        // Then expect exception
    }

    @Test
    fun `StreamWSAuthMessageRequest parses canonical json`() {
        val adapter = moshi.adapter(StreamWSAuthMessageRequest::class.java)
        val json =
            """
            {
              "products": ["chat", "video"],
              "token": "jwt-123",
              "user_details": {
                "id": "user-7",
                "image": "https://example.test/u7.png",
                "invisible": false,
                "language": "en",
                "name": "Seven",
                "custom": {"role": "admin"}
              }
            }
            """
                .trimIndent()

        val request = adapter.fromJson(json)!!

        assertEquals(listOf("chat", "video"), request.products)
        assertEquals("jwt-123", request.token)
        assertEquals("user-7", request.userDetails.id)
        assertEquals("https://example.test/u7.png", request.userDetails.image)
        assertEquals(false, request.userDetails.invisible)
        assertEquals("admin", request.userDetails.custom?.get("role"))
    }

    @Test
    fun `StreamClientConnectedEvent parses canonical json`() {
        val adapter = moshi.adapter(StreamClientConnectedEvent::class.java)
        val json =
            """
            {
              "connection_id": "conn-xyz",
              "me": {
                "created_at": 12312412312,
                "id": "user-42",
                "language": "en",
                "role": "admin",
                "updated_at": 12312412312,
                "blocked_user_ids": ["u-1"],
                "teams": ["team-a"],
                "custom": {"source": "json"},
                "deactivated_at": null,
                "deleted_at": null,
                "image": "https://example.test/avatar.png",
                "last_active": null,
                "name": "User 42"
              },
              "type": "connection.ok"
            }
            """
                .trimIndent()

        val event = adapter.fromJson(json)!!

        assertEquals("conn-xyz", event.connectionId)
        assertEquals("connection.ok", event.type)
        assertEquals("user-42", event.me.id)
        assertEquals(listOf("team-a"), event.me.teams)
        assertEquals(mapOf("source" to "json"), event.me.custom)
    }

    @Test
    fun `StreamClientConnectionErrorEvent parses canonical json`() {
        val adapter = moshi.adapter(StreamClientConnectionErrorEvent::class.java)
        val json =
            """
            {
              "connection_id": "conn-bad",
              "created_at": 12312412312,
              "error": {
                "code": 32,
                "duration": "5ms",
                "message": "Failure",
                "more_info": "https://example.test/help",
                "StatusCode": 503,
                "details": [10, 20],
                "unrecoverable": false,
                "exception_fields": {"reason": "maintenance"}
              },
              "type": "connection.error"
            }
            """
                .trimIndent()

        val event = adapter.fromJson(json)!!

        assertEquals("conn-bad", event.connectionId)
        assertEquals("connection.error", event.type)
        assertEquals(32, event.error.code)
        assertEquals("maintenance", event.error.exceptionFields?.get("reason"))
    }

    @Test
    fun `StreamHealthCheckEvent parses canonical json`() {
        val adapter = moshi.adapter(StreamHealthCheckEvent::class.java)
        val json =
            """
            {
              "connection_id": "conn-health",
              "created_at": 12312412312,
              "custom": {"ping": "pong"},
              "type": "health.check"
            }
            """
                .trimIndent()

        val event = adapter.fromJson(json)!!

        assertEquals("conn-health", event.connectionId)
        assertEquals(mapOf("ping" to "pong"), event.custom)
    }
}
