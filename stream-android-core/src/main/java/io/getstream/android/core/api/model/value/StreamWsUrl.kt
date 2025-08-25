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

import io.getstream.android.core.annotations.StreamCoreApi
import java.net.URI

/**
 * Represents a WebSocket URL.
 *
 * @property rawValue The raw value of the WebSocket URL.
 */
@StreamCoreApi
@JvmInline
value class StreamWsUrl private constructor(val rawValue: String) {
    companion object {
        /**
         * Creates a new [StreamWsUrl] from a string.
         *
         * @param value The string value of the WS URL.
         * @return The created [StreamWsUrl].
         * @throws IllegalArgumentException If the value is blank or not a valid WS URL.
         */
        fun fromString(value: String): StreamWsUrl {
            require(value.isNotBlank()) { "WS URL must not be blank" }
            val validUrl =
                try {
                    URI.create(value)
                } catch (e: Exception) {
                    null
                }
            requireNotNull(validUrl) { "Invalid WS URL: $value" }
            return StreamWsUrl(value)
        }
    }
}
