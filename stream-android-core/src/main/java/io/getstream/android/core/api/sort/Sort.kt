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
package io.getstream.android.core.api.sort

import io.getstream.android.core.annotations.StreamPublishedApi

/**
 * A sort configuration that combines a sort field with a direction.
 *
 * This class represents a complete sort specification that can be applied to collections of the
 * associated model type. It provides both local sorting capabilities and the ability to generate
 * remote API request parameters.
 */
@StreamPublishedApi
public open class Sort<T>(public val field: SortField<T>, public val direction: SortDirection) :
    Comparator<T> {

    /** Converts this sort configuration to a DTO map for API requests. */
    public fun toDto(): Map<String, Any> =
        mapOf("field" to field.remote, "direction" to direction.value)

    override fun compare(o1: T?, o2: T?): Int {
        return field.comparator.compare(o1, o2, direction)
    }
}
