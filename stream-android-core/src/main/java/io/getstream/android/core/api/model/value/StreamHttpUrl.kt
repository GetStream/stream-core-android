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

import io.getstream.android.core.annotations.StreamInternalApi
import java.net.URI

/**
 * Represents an HTTP URL.
 *
 * @property rawValue The raw value of the HTTP URL.
 */
@StreamInternalApi
@JvmInline
public value class StreamHttpUrl(public val rawValue: String) {
    public companion object {
        /**
         * Creates a new [StreamHttpUrl] from a string.
         *
         * @param value The string value of the HTTP URL.
         * @return The created [StreamHttpUrl].
         * @throws IllegalArgumentException If the value is blank or not a valid URL.
         */
        public fun fromString(value: String): StreamHttpUrl {
            require(value.isNotBlank()) { "HTTP URL must not be blank" }
            val validURl =
                try {
                    URI.create(value)
                } catch (e: Exception) {
                    null
                }
            requireNotNull(validURl) { "Invalid HTTP URL: $value" }
            return StreamHttpUrl(value)
        }
    }
}
