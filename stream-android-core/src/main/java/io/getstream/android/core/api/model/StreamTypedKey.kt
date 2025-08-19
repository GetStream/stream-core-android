/*
 * Copyright (c) 2014-2025 Stream.io Inc. All rights reserved.
 *
 * Licensed under the Stream License;
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://github.com/GetStream/stream-android-base/blob/main/LICENSE
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.getstream.android.core.api.model

import io.getstream.android.core.annotations.StreamCoreApi

/**
 * A typed key that can be used to disambiguate between different types of requests.
 *
 * @property id The unique identifier for the key.
 */
@StreamCoreApi
data class StreamTypedKey<T>(val id: Any) {

    companion object {
        /**
         * Creates a new [StreamTypedKey] with the given [id] and type [T].
         *
         * @param T The type of the key.
         * @param id The unique identifier for the key.
         * @return A new [StreamTypedKey] with the given [id] and type [T].
         */
        inline fun <reified T> Any.asStreamTypedKey() = StreamTypedKey<T>(this)

        /**
         * Creates a new [StreamTypedKey] with a random [id] and type [T].
         *
         * @param T The type of the key.
         * @return A new [StreamTypedKey] with a random [id] and type [T].
         */
        fun <T> randomExecutionKey() = StreamTypedKey<T>(Any())
    }
}
