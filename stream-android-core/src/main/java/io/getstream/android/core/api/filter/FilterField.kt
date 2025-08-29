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
package io.getstream.android.core.api.filter

/**
 * Interface representing a field that can be used in filters for querying data from the Stream API.
 *
 * Implementations of this interface should provide [remote], which is the name of the field as
 * expected by the Stream API.
 */
public interface FilterField {
    /** The name of this field as expected by the Stream API. */
    public val remote: String
}
