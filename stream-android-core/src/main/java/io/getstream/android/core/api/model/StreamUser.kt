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

package io.getstream.android.core.api.model

import io.getstream.android.core.annotations.StreamInternalApi
import io.getstream.android.core.api.model.value.StreamUserId

/**
 * Represents a user in the Stream system.
 *
 * @property id The unique identifier for the user.
 * @property name The name of the user (optional).
 * @property imageURL The URL of the user's image (optional).
 * @property customData Custom data associated with the user, represented as a map (default empty
 *   map).
 */
@StreamInternalApi
public data class StreamUser(
    public val id: StreamUserId,
    public val name: String? = null,
    public val imageURL: String? = null,
    public val customData: Map<String, Any> = emptyMap(),
)
