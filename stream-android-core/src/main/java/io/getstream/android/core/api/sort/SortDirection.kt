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
 * The direction of a sort operation. This enum defines whether a sort should be performed in
 * ascending (forward) or descending (reverse) order. The raw values correspond to the values
 * expected by the remote API.
 */
@StreamPublishedApi
public enum class SortDirection(public val value: Int) {
    /** Sort in ascending order (A to Z, 1 to 9, etc.). */
    FORWARD(1),

    /** Sort in descending order (Z to A, 9 to 1, etc.). */
    REVERSE(-1),
}
