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
package io.getstream.android.core.internal.state

import io.getstream.android.core.api.model.connection.StreamConnectionState
import io.getstream.android.core.api.state.StreamClientState

/** Mutable version of [StreamClientState]. */
internal interface MutableStreamClientState : StreamClientState {

    /**
     * Updates the connection state.
     *
     * @param connectionState The new connection state.
     * @return The result of the operation.
     */
    fun update(connectionState: StreamConnectionState): Result<Unit>
}
