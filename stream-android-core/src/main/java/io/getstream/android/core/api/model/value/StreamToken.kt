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
 * Authentication token value-object.
 *
 * Always construct it with [fromString] so we can enforce invariants.
 */
@StreamPublishedApi
@JvmInline
public value class StreamToken private constructor(public val rawValue: String) {
    public companion object {
        /**
         * Creates a new [StreamToken] from a string.
         *
         * @param token The string value of the token.
         * @return The created [StreamToken].
         * @throws IllegalArgumentException If the token is blank.
         */
        public fun fromString(token: String): StreamToken {
            require(token.isNotBlank()) { "Token must not be blank" }
            return StreamToken(token)
        }
    }
}
