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
package io.getstream.android.core.internal.observers.network

import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import io.getstream.android.core.api.log.StreamLogger
import io.getstream.android.core.api.model.connection.network.StreamNetworkInfo
import io.getstream.android.core.api.observers.network.StreamNetworkMonitorListener
import io.getstream.android.core.api.subscribe.StreamSubscription
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager
import io.mockk.MockKAnnotations
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.N])
internal class StreamNetworkMonitorImplTest {

    @MockK(relaxed = true) lateinit var logger: StreamLogger
    @MockK(relaxed = true) lateinit var connectivityManager: ConnectivityManager
    @MockK(relaxed = true) lateinit var snapshotBuilder: StreamNetworkSnapshotBuilder

    private lateinit var scope: TestScope
    private lateinit var subscriptionManager: RecordingSubscriptionManager
    private lateinit var monitor: StreamNetworkMonitorImpl

    private val listener = mockk<StreamNetworkMonitorListener>(relaxed = true)

    @BeforeTest
    fun setUp() {
        MockKAnnotations.init(this)
        scope = TestScope(StandardTestDispatcher())
        subscriptionManager = RecordingSubscriptionManager()
        monitor =
            StreamNetworkMonitorImpl(
                logger = logger,
                scope = scope,
                streamSubscriptionManager = subscriptionManager,
                snapshotBuilder = snapshotBuilder,
                connectivityManager = connectivityManager,
            )
        monitor.subscribe(listener)
    }

    @Test
    fun `start registers callback and emits initial snapshot`() = runTest {
        val network = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities>()
        val linkProperties = mockk<LinkProperties>()
        val snapshot = mockk<StreamNetworkInfo.Snapshot>()

        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities
        every { connectivityManager.getLinkProperties(network) } returns linkProperties
        every { snapshotBuilder.build(network, capabilities, linkProperties) } returns
            Result.success(snapshot)

        val callbackSlot = slot<StreamNetworkMonitorCallback>()
        every { connectivityManager.registerDefaultNetworkCallback(capture(callbackSlot)) } just
            runs

        monitor.start().getOrThrow()
        callbackSlot.captured.onAvailable(network)
        scope.advanceUntilIdle()

        verify { connectivityManager.registerDefaultNetworkCallback(any()) }
        coVerify { listener.onNetworkConnected(snapshot) }
    }

    @Test
    fun `snapshot failure during update does not notify`() = runTest {
        val network = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities>()
        val linkProperties = mockk<LinkProperties>()
        val initialSnapshot = mockk<StreamNetworkInfo.Snapshot>()

        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities
        every { connectivityManager.getLinkProperties(network) } returns linkProperties
        every { snapshotBuilder.build(network, capabilities, linkProperties) } returnsMany
            listOf(Result.success(initialSnapshot), Result.failure(IllegalStateException("boom")))

        val callbackSlot = slot<StreamNetworkMonitorCallback>()
        every { connectivityManager.registerDefaultNetworkCallback(capture(callbackSlot)) } just
            runs

        monitor.start()
        callbackSlot.captured.onAvailable(network)
        scope.advanceUntilIdle()

        callbackSlot.captured.onCapabilitiesChanged(network, capabilities)
        scope.advanceUntilIdle()

        coVerify(exactly = 1) { listener.onNetworkConnected(initialSnapshot) }
    }

    @Test
    fun `network loss notifies listeners`() = runTest {
        val network = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities>()
        val linkProperties = mockk<LinkProperties>()
        val snapshot = mockk<StreamNetworkInfo.Snapshot>()

        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities
        every { connectivityManager.getLinkProperties(network) } returns linkProperties
        every { snapshotBuilder.build(network, capabilities, linkProperties) } returns
            Result.success(snapshot)

        val callbackSlot = slot<StreamNetworkMonitorCallback>()
        every { connectivityManager.registerDefaultNetworkCallback(capture(callbackSlot)) } just
            runs

        monitor.start()
        val callback = callbackSlot.captured
        callback.onAvailable(network)
        scope.advanceUntilIdle()

        callback.onLost(network)
        scope.advanceUntilIdle()
        coVerify { listener.onNetworkLost(false) }

        callback.onAvailable(network)
        scope.advanceUntilIdle()
        callback.onUnavailable()
        scope.advanceUntilIdle()
        coVerify { listener.onNetworkLost(true) }
    }

    @Test
    fun `stop unregisters callback`() = runTest {
        val network = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities>()
        val linkProperties = mockk<LinkProperties>()
        val snapshot = mockk<StreamNetworkInfo.Snapshot>()

        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities
        every { connectivityManager.getLinkProperties(network) } returns linkProperties
        every { snapshotBuilder.build(network, capabilities, linkProperties) } returns
            Result.success(snapshot)

        val callbackSlot = slot<StreamNetworkMonitorCallback>()
        every { connectivityManager.registerDefaultNetworkCallback(capture(callbackSlot)) } just
            runs
        every {
            connectivityManager.unregisterNetworkCallback(
                any<ConnectivityManager.NetworkCallback>()
            )
        } just runs

        monitor.start()
        val callback = callbackSlot.captured
        monitor.stop().getOrThrow()

        verify { connectivityManager.unregisterNetworkCallback(callback) }
    }

    private class RecordingSubscriptionManager :
        StreamSubscriptionManager<StreamNetworkMonitorListener> {
        private val listeners = mutableSetOf<StreamNetworkMonitorListener>()

        override fun subscribe(
            listener: StreamNetworkMonitorListener,
            options: StreamSubscriptionManager.Options,
        ): Result<StreamSubscription> {
            listeners += listener
            return Result.success(mockk(relaxed = true))
        }

        override fun clear(): Result<Unit> {
            listeners.clear()
            return Result.success(Unit)
        }

        override fun forEach(block: (StreamNetworkMonitorListener) -> Unit): Result<Unit> {
            listeners.forEach(block)
            return Result.success(Unit)
        }
    }
}
