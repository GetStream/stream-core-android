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
package io.getstream.android.core.internal.model.events

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.getstream.android.core.api.model.connection.StreamConnectedUser
import io.getstream.android.core.api.model.event.StreamClientWsEvent
import io.getstream.android.core.api.model.exceptions.StreamEndpointErrorData
import java.util.Date

/**
 * Represents the "connection.error" event type.
 *
 * Note: This event is not specified in the OpenAPI spec, so we define it manually.
 */
internal const val EVENT_TYPE_CONNECTION_ERROR = "connection.error"

/**
 * Represents the "connection.ok" event type.
 *
 * Note: This event is not specified in the OpenAPI spec, so we define it manually.
 */
internal const val EVENT_TYPE_CONNECTION_OK = "connection.ok"

/**
 * Represents a WebSocket event that is sent when the connection is established successfully.
 *
 * Note: This event is not specified in the OpenAPI spec, so we define it manually.
 *
 * @property connectionId The unique identifier for the connection.
 * @property me The own user response containing user details.
 * @property type The type of the event.
 */
@JsonClass(generateAdapter = true)
internal data class StreamClientConnectedEvent(
    @Json(name = "connection_id") val connectionId: String,
    @Json(name = "me") val me: StreamConnectedUser,
    @Json(name = "type") val type: String,
) : StreamClientWsEvent

/**
 * Represents a WebSocket event that is sent when there is an error in the connection.
 *
 * Note: This event is not specified in the OpenAPI spec, so we define it manually.
 *
 * @property connectionId The unique identifier for the connection.
 * @property createdAt The timestamp when the error occurred.
 * @property error The API error details.
 * @property type The type of the event.
 */
@JsonClass(generateAdapter = true)
internal data class StreamClientConnectionErrorEvent(
    @Json(name = "connection_id") val connectionId: String,
    @Json(name = "created_at") val createdAt: Date,
    @Json(name = "error") val error: StreamEndpointErrorData,
    @Json(name = "type") val type: String,
) : StreamClientWsEvent
