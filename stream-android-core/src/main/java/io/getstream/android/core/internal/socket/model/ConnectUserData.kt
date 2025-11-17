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

package io.getstream.android.core.internal.socket.model

/**
 * Data class representing the user data required to connect to the socket.
 *
 * @property userId The ID of the user.
 * @property token The authentication token.
 * @property name The name of the user.
 * @property image The URL of the user's profile image.
 * @property invisible Whether the user is invisible.
 * @property language The language of the user.
 * @property custom The custom data of the user.
 */
internal data class ConnectUserData(
    val userId: String,
    val token: String,
    val name: String? = null,
    val image: String? = null,
    val invisible: Boolean = false,
    val language: String? = null,
    val custom: Map<String, Any?>? = null,
)
