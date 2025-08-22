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
package io.getstream.android.core.api.model.connection

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.getstream.android.core.annotations.StreamCoreApi
import java.util.Date
import kotlin.collections.Map

/**
 * Represents the user that is connected to the client.
 *
 * @property createdAt The date and time when the user was created.
 * @property id The unique identifier of the user.
 * @property language The language of the user.
 * @property role The role of the user.
 * @property updatedAt The date and time when the user was last updated.
 * @property blockedUserIds The list of user IDs that are blocked by the user.
 * @property teams The list of teams that the user belongs to.
 * @property custom The custom data associated with the user.
 * @property deactivatedAt The date and time when the user was deactivated.
 * @property deletedAt The date and time when the user was deleted.
 * @property image The URL of the user's profile image.
 * @property lastActive The date and time when the user was last active.
 * @property name The name of the user.
 */
@StreamCoreApi
@JsonClass(generateAdapter = true)
class StreamConnectedUser(
    @Json(name = "created_at") val createdAt: Date,
    @Json(name = "id") val id: String,
    @Json(name = "language") val language: String,
    @Json(name = "role") val role: String,
    @Json(name = "updated_at") val updatedAt: Date,
    @Json(name = "blocked_user_ids") val blockedUserIds: List<String> = emptyList(),
    @Json(name = "teams") val teams: List<String>,
    @Json(name = "custom") val custom: Map<String, Any?> = emptyMap(),
    @Json(name = "deactivated_at") val deactivatedAt: Date? = null,
    @Json(name = "deleted_at") val deletedAt: Date? = null,
    @Json(name = "image") val image: String? = null,
    @Json(name = "last_active") val lastActive: Date? = null,
    @Json(name = "name") val name: String? = null,
) {
    /**
     * Returns a string representation of the [StreamConnectedUser] object.
     *
     * @return A string.
     */
    override fun toString(): String =
        "StreamConnectedUser(createdAt=$createdAt, id=$id, language=$language, role=$role, updatedAt=$updatedAt, blockedUserIds=$blockedUserIds, teams=$teams, custom=$custom, deactivatedAt=$deactivatedAt, deletedAt=$deletedAt, image=$image, lastActive=$lastActive, name=$name)"
}
