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

import io.getstream.android.core.api.model.connection.network.StreamNetworkInfo
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
internal class StreamNetworkMonitorListenerTest {

    @Test
    fun `default listener implementations no-op`() = runTest {
        val listener = object : StreamNetworkMonitorListener {}

        listener.onNetworkConnected(null)
        listener.onNetworkLost()
        listener.onNetworkPropertiesChanged(StreamNetworkInfo.Snapshot(transports = emptySet()))
    }

    @Test
    fun `overrides receive the expected payloads`() = runTest {
        val snapshots = mutableListOf<StreamNetworkInfo.Snapshot?>()
        var lostFlag: Boolean? = null

        val listener =
            object : StreamNetworkMonitorListener {
                override suspend fun onNetworkConnected(snapshot: StreamNetworkInfo.Snapshot?) {
                    snapshots += snapshot
                }

                override suspend fun onNetworkLost(permanent: Boolean) {
                    lostFlag = permanent
                }

                override suspend fun onNetworkPropertiesChanged(
                    snapshot: StreamNetworkInfo.Snapshot
                ) {
                    snapshots += snapshot
                }
            }

        val connected = StreamNetworkInfo.Snapshot(transports = emptySet())
        val updated = connected.copy(priority = StreamNetworkInfo.PriorityHint.LATENCY)

        listener.onNetworkConnected(connected)
        listener.onNetworkPropertiesChanged(updated)
        listener.onNetworkLost(permanent = true)

        assertContentEquals(listOf(connected, updated), snapshots)
        assertTrue(lostFlag == true)
    }
}
