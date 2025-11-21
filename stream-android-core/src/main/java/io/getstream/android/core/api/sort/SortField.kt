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

import io.getstream.android.core.annotations.StreamInternalApi
import io.getstream.android.core.annotations.StreamPublishedApi
import io.getstream.android.core.internal.sort.SortFieldImpl

/**
 * A protocol that defines a sortable field for a specific model type.
 *
 * This interface provides the foundation for creating sortable fields that can be used both for
 * local sorting and remote API requests. It includes a comparator for local sorting operations and
 * a remote string identifier for API communication.
 */
@StreamPublishedApi
public interface SortField<T> {
    /** A comparator that can be used for local sorting operations. */
    public val comparator: AnySortComparator<T>

    /** The string identifier used when sending sort parameters to the remote API. */
    public val remote: String

    public companion object {
        /**
         * Creates a new sort field with the specified remote identifier and local value extractor.
         *
         * @param remote The string identifier used for remote API requests
         * @param localValue A function that extracts the comparable value from a model instance
         */
        @StreamInternalApi
        public fun <T, V : Comparable<V>> create(
            remote: String,
            localValue: (T) -> V,
        ): SortField<T> = SortFieldImpl(remote, localValue)
    }
}
