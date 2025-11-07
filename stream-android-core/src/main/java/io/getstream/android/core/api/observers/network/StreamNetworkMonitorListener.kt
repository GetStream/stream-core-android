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

package io.getstream.android.core.api.observers.network

import io.getstream.android.core.annotations.StreamInternalApi
import io.getstream.android.core.api.model.connection.network.StreamNetworkInfo

/**
 * Listener interface for network state changes.
 *
 * Implement this interface to receive updates about network state changes.
 */
@StreamInternalApi
public interface StreamNetworkMonitorListener {
    /**
     * Called when the network is connected.
     *
     * @param snapshot A [StreamNetworkInfo.Snapshot] describing the newly connected network.
     */
    public suspend fun onNetworkConnected(snapshot: StreamNetworkInfo.Snapshot?) {}

    /**
     * Called when the network is lost.
     *
     * @param permanent True if the network is lost permanently (e.g., due to airplane mode).
     */
    public suspend fun onNetworkLost(permanent: Boolean = false) {}

    /**
     * Called when the properties of the currently connected network change while the connection
     * remains active.
     *
     * @param snapshot A [StreamNetworkInfo.Snapshot] containing the updated properties.
     */
    public suspend fun onNetworkPropertiesChanged(snapshot: StreamNetworkInfo.Snapshot) {}
}
