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

package io.getstream.android.core.internal.observers

import io.getstream.android.core.api.model.connection.lifecycle.StreamLifecycleState
import io.getstream.android.core.api.model.connection.network.StreamNetworkState

/** Listener interface for receiving network and lifecycle state updates. */
internal interface StreamNetworkAndLifecycleMonitorListener {

    /**
     * Called when the network or lifecycle state changes.
     *
     * @param networkState The new network state.
     * @param lifecycleState The new lifecycle state.
     */
    fun onNetworkAndLifecycleState(
        networkState: StreamNetworkState,
        lifecycleState: StreamLifecycleState,
    )
}
