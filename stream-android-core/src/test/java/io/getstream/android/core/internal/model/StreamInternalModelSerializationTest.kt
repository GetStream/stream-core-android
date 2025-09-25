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
import io.getstream.android.core.api.model.exceptions.StreamEndpointErrorData
import io.getstream.android.core.internal.model.authentication.StreamWSAuthMessageRequest
import io.getstream.android.core.internal.model.events.EVENT_TYPE_CONNECTION_ERROR
import io.getstream.android.core.internal.model.events.EVENT_TYPE_CONNECTION_OK
import io.getstream.android.core.internal.model.events.StreamClientConnectedEvent
import io.getstream.android.core.internal.model.events.StreamClientConnectionErrorEvent
import io.getstream.android.core.internal.model.events.StreamHealthCheckEvent
import io.getstream.android.core.internal.serialization.moshi.StreamCoreMoshiProvider
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

internal class StreamInternalModelSerializationTest {

    private val moshi = StreamCoreMoshiProvider().builder {}.build()

    @Test
    fun `StreamWSAuthMessageRequest round trips through Moshi`() {
        // Given
        val details =
            StreamConnectUserDetailsRequest(
                id = "user-1",
                image = "https://example.test/avatar.png",
                invisible = true,
                language = "en",
                name = "User One",
                custom = mapOf("role" to "admin", "tier" to "gold"),
            )
        val request =
            StreamWSAuthMessageRequest(
                products = listOf("chat", "video"),
                token = "jwt-token",
                userDetails = details,
            )

        val adapter = moshi.adapter(StreamWSAuthMessageRequest::class.java)

        // When
        val json = adapter.toJson(request)
        val decoded = adapter.fromJson(json)!!

        // Then
        assertEquals(request, decoded)
    }

    @Test(expected = JsonDataException::class)
    fun `StreamWSAuthMessageRequest missing required fields yields null`() {
        // Given
        val adapter = moshi.adapter(StreamWSAuthMessageRequest::class.java)
        val invalidJson = "{\"products\":null,\"token\":null}"

        // When
        adapter.fromJson(invalidJson)

        // Then expect exception
    }

    @Test(expected = JsonDataException::class)
    fun `StreamClientConnectedEvent missing connectionId yields null`() {
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
    fun `StreamClientConnectedEvent serializes expected user info`() {
        // Given
        val now = Date(1_000L)
        val user =
            StreamConnectedUser(
                createdAt = now,
                id = "user-1",
                language = "en",
                role = "user",
                updatedAt = now,
                teams = listOf("team-a"),
                custom = mapOf("ref" to "123"),
                name = "User",
            )
        val event =
            StreamClientConnectedEvent(
                connectionId = "conn-123",
                me = user,
                type = EVENT_TYPE_CONNECTION_OK,
            )

        val adapter = moshi.adapter(StreamClientConnectedEvent::class.java)

        // When
        val decoded = adapter.fromJson(adapter.toJson(event))!!

        // Then
        assertEquals(event.connectionId, decoded.connectionId)
        assertEquals(event.type, decoded.type)
        assertEquals(user.id, decoded.me.id)
        assertEquals(user.language, decoded.me.language)
        assertEquals(user.custom, decoded.me.custom)
        assertEquals(user.teams, decoded.me.teams)
        assertEquals(user.name, decoded.me.name)
    }

    @Test
    fun `StreamClientConnectionErrorEvent includes endpoint error data`() {
        // Given
        val error =
            StreamEndpointErrorData(
                code = 40,
                duration = "12ms",
                message = "Unauthorized",
                moreInfo = "https://example.test/docs",
                statusCode = 401,
                details = listOf(1, 2, 3),
                unrecoverable = true,
                exceptionFields = mapOf("token" to "expired"),
            )
        val event =
            StreamClientConnectionErrorEvent(
                connectionId = "conn-err",
                createdAt = Date(500L),
                error = error,
                type = EVENT_TYPE_CONNECTION_ERROR,
            )

        val adapter = moshi.adapter(StreamClientConnectionErrorEvent::class.java)

        // When
        val decoded = adapter.fromJson(adapter.toJson(event))!!

        // Then
        assertEquals(event.connectionId, decoded.connectionId)
        assertEquals(event.type, decoded.type)
        assertEquals(error, decoded.error)
    }

    @Test
    fun `StreamHealthCheckEvent defaults custom map`() {
        // Given
        val adapter = moshi.adapter(StreamHealthCheckEvent::class.java)
        val event =
            StreamHealthCheckEvent(
                connectionId = "conn-health",
                createdAt = Date(100L),
                type = "health.check",
            )

        // When
        val decoded = adapter.fromJson(adapter.toJson(event))!!

        // Then
        assertEquals(event.connectionId, decoded.connectionId)
        assertTrue(decoded.custom.isEmpty())
        assertEquals(event.type, decoded.type)
    }
}
