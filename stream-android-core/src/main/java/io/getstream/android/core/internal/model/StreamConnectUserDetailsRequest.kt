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

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class StreamConnectUserDetailsRequest(
    @Json(name = "id") val id: String,
    @Json(name = "image") val image: String? = null,
    @Json(name = "invisible") val invisible: Boolean? = null,
    @Json(name = "language") val language: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "custom") val custom: Map<String, Any?>? = null,
)
