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
package io.getstream.android.core.api.model.value

import android.annotation.SuppressLint

/**
 * Represents a user ID.
 *
 * @property rawValue The raw value of the user ID.
 */
@SuppressLint("StreamCoreApiMissing")
@JvmInline
value class StreamUserId private constructor(val rawValue: String) {
    companion object {
        /**
         * Creates a [StreamUserId] from a [rawValue].
         *
         * @param rawValue The raw value of the user ID.
         * @return A [StreamUserId] instance.
         */
        fun fromString(rawValue: String): StreamUserId {
            require(rawValue.isNotBlank()) { "User ID cannot be blank" }
            return StreamUserId(rawValue)
        }
    }
}
