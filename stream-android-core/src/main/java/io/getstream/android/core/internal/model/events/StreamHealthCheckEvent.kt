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
import io.getstream.android.core.annotations.StreamPublishedApi
import io.getstream.android.core.api.model.event.StreamClientWsEvent
import java.util.Date

@StreamPublishedApi
@JsonClass(generateAdapter = true)
internal data class StreamHealthCheckEvent(
    @Json(name = "connection_id") val connectionId: String,
    @Json(name = "created_at") val createdAt: Date,
    @Json(name = "custom") val custom: Map<String, Any?> = emptyMap(),
    @Json(name = "type") val type: String,
) : StreamClientWsEvent
