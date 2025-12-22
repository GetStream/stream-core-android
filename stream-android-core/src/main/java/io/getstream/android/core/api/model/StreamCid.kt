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

package io.getstream.android.core.api.model

import io.getstream.android.core.annotations.StreamPublishedApi

@ConsistentCopyVisibility
@StreamPublishedApi
public data class StreamCid private constructor(public val type: String, public val id: String) {

    public companion object {

        public fun parse(cid: String): StreamCid {
            require(cid.isNotEmpty())
            val split = cid.split(":")
            require(split.size == 2)
            return StreamCid(split[0], split[1])
        }

        public fun fromTypeAndId(type: String, id: String): StreamCid {
            require(type.isNotEmpty())
            require(id.isNotEmpty())
            return StreamCid(type, id)
        }
    }

    public fun formatted(): String = "$type:$id"
}
