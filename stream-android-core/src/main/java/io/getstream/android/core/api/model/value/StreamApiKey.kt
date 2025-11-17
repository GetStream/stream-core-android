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

import io.getstream.android.core.annotations.StreamPublishedApi

/**
 * Represents an API key for authentication.
 *
 * @property rawValue The raw value of the API key.
 */
@StreamPublishedApi
@JvmInline
public value class StreamApiKey private constructor(public val rawValue: String) {
    public companion object {
        /**
         * Creates a new [StreamApiKey] from a string.
         *
         * @param value The string value of the API key.
         * @return The created [StreamApiKey].
         * @throws IllegalArgumentException If the value is blank or contains only digits.
         */
        public fun fromString(value: String): StreamApiKey {
            require(value.isNotBlank()) { "API key must not be blank" }
            return StreamApiKey(value)
        }
    }
}
