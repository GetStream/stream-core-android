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
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.R])
internal class StreamNetworkMonitorCallbackTest {

    @MockK(relaxed = true) lateinit var logger: StreamLogger
    @MockK(relaxed = true) lateinit var connectivityManager: ConnectivityManager
    @MockK(relaxed = true) lateinit var snapshotBuilder: StreamNetworkSnapshotBuilder

    private lateinit var scope: TestScope
    private lateinit var subscriptionManager: RecordingSubscriptionManager
    private lateinit var callback: StreamNetworkMonitorCallback
    private lateinit var primaryListener: StreamNetworkMonitorListener
    private lateinit var secondaryListener: StreamNetworkMonitorListener

    @BeforeTest
    fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        scope = TestScope(StandardTestDispatcher())
        subscriptionManager = RecordingSubscriptionManager()
        primaryListener = mockk(relaxed = true)
        secondaryListener = mockk(relaxed = true)
        subscriptionManager.subscribe(primaryListener).getOrThrow()
        subscriptionManager.subscribe(secondaryListener).getOrThrow()

        callback =
            StreamNetworkMonitorCallback(
                logger = logger,
                scope = scope,
                subscriptionManager = subscriptionManager,
                snapshotBuilder = snapshotBuilder,
                connectivityManager = connectivityManager,
            )
    }

    @AfterTest
    fun tearDown() {
        clearMocks(
            logger,
            connectivityManager,
            snapshotBuilder,
            primaryListener,
            secondaryListener,
            answers = false,
        )
    }

    @Test
    fun `onRegistered emits initial snapshot when default network available`() {
        val network = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities>()
        val linkProperties = mockk<LinkProperties>()
        val snapshot = mockk<StreamNetworkInfo.Snapshot>()

        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities
        every { connectivityManager.getLinkProperties(network) } returns linkProperties
        every { snapshotBuilder.build(network, capabilities, linkProperties) } returns Result.success(snapshot)

        callback.onRegistered()
        scope.advanceUntilIdle()

        coVerify { primaryListener.onNetworkConnected(snapshot) }
        coVerify { secondaryListener.onNetworkConnected(snapshot) }
    }

    @Test
    fun `onRegistered skips when no default network`() {
        every { connectivityManager.activeNetwork } returns null
        every { connectivityManager.allNetworks } returns emptyArray()

        callback.onRegistered()
        scope.advanceUntilIdle()

        verify(exactly = 0) { connectivityManager.getNetworkCapabilities(any()) }
        coVerify(exactly = 0) { primaryListener.onNetworkConnected(any()) }
        coVerify(exactly = 0) { secondaryListener.onNetworkConnected(any()) }
    }

    @Test
    fun `onAvailable ignores non-default network`() {
        val defaultNetwork = mockk<Network>()
        val otherNetwork = mockk<Network>()

        every { connectivityManager.activeNetwork } returns defaultNetwork

        callback.onAvailable(otherNetwork)
        scope.advanceUntilIdle()

        verify(exactly = 0) { connectivityManager.getNetworkCapabilities(otherNetwork) }
        verify(exactly = 0) { snapshotBuilder.build(any(), any(), any()) }
        coVerify(exactly = 0) { primaryListener.onNetworkConnected(any()) }
    }

    @Test
    fun `onAvailable publishes snapshot for default network`() {
        val network = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities>()
        val linkProperties = mockk<LinkProperties>()
        val snapshot = mockk<StreamNetworkInfo.Snapshot>()

        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities
        every { connectivityManager.getLinkProperties(network) } returns linkProperties
        every { snapshotBuilder.build(network, capabilities, linkProperties) } returns Result.success(snapshot)

        callback.onAvailable(network)
        scope.advanceUntilIdle()

        coVerify { primaryListener.onNetworkConnected(snapshot) }
        coVerify { secondaryListener.onNetworkConnected(snapshot) }
    }

    @Test
    fun `onAvailable logs snapshot build failure`() {
        val network = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities>()
        val linkProperties = mockk<LinkProperties>()
        val error = IllegalStateException("boom")

        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities
        every { connectivityManager.getLinkProperties(network) } returns linkProperties
        every { snapshotBuilder.build(network, capabilities, linkProperties) } returns Result.failure(error)

        callback.onAvailable(network)
        scope.advanceUntilIdle()

        verify { logger.e(error, match { it?.invoke()?.contains("Failed to assemble network snapshot") == true }) }
        coVerify(exactly = 0) { primaryListener.onNetworkConnected(any()) }
    }

    @Test
    fun `capabilities change triggers properties update when snapshot differs`() {
        val network = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities>()
        val linkProperties = mockk<LinkProperties>()
        val initialSnapshot = mockk<StreamNetworkInfo.Snapshot>()
        val updatedSnapshot = mockk<StreamNetworkInfo.Snapshot>()

        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities
        every { connectivityManager.getLinkProperties(network) } returns linkProperties
        every {
            snapshotBuilder.build(network, capabilities, linkProperties)
        } returnsMany listOf(Result.success(initialSnapshot), Result.success(updatedSnapshot))

        callback.onAvailable(network)
        scope.advanceUntilIdle()

        callback.onCapabilitiesChanged(network, capabilities)
        scope.advanceUntilIdle()

        coVerify { primaryListener.onNetworkConnected(initialSnapshot) }
        coVerify { primaryListener.onNetworkPropertiesChanged(updatedSnapshot) }
        coVerify { secondaryListener.onNetworkPropertiesChanged(updatedSnapshot) }
    }

    @Test
    fun `capabilities change with identical snapshot does nothing`() {
        val network = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities>()
        val linkProperties = mockk<LinkProperties>()
        val snapshot = mockk<StreamNetworkInfo.Snapshot>()

        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities
        every { connectivityManager.getLinkProperties(network) } returns linkProperties
        every {
            snapshotBuilder.build(network, capabilities, linkProperties)
        } returnsMany listOf(Result.success(snapshot), Result.success(snapshot))

        callback.onAvailable(network)
        scope.advanceUntilIdle()

        callback.onCapabilitiesChanged(network, capabilities)
        scope.advanceUntilIdle()

        coVerify(exactly = 0) { primaryListener.onNetworkPropertiesChanged(any()) }
        coVerify(exactly = 1) { primaryListener.onNetworkConnected(snapshot) }
    }

    @Test
    fun `link properties change triggers properties update`() {
        val network = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities>()
        val initialLink = mockk<LinkProperties>()
        val updatedLink = mockk<LinkProperties>()
        val initialSnapshot = mockk<StreamNetworkInfo.Snapshot>()
        val updatedSnapshot = mockk<StreamNetworkInfo.Snapshot>()

        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities
        every { connectivityManager.getLinkProperties(network) } returnsMany listOf(initialLink, updatedLink)
        every {
            snapshotBuilder.build(network, capabilities, any())
        } returnsMany listOf(Result.success(initialSnapshot), Result.success(updatedSnapshot))

        callback.onAvailable(network)
        scope.advanceUntilIdle()

        callback.onLinkPropertiesChanged(network, updatedLink)
        scope.advanceUntilIdle()

        coVerify { primaryListener.onNetworkPropertiesChanged(updatedSnapshot) }
    }

    @Test
    fun `lost network clears state and notifies listeners`() {
        val network = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities>()
        val linkProperties = mockk<LinkProperties>()
        val snapshot = mockk<StreamNetworkInfo.Snapshot>()

        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities
        every { connectivityManager.getLinkProperties(network) } returns linkProperties
        every { snapshotBuilder.build(network, capabilities, linkProperties) } returns Result.success(snapshot)

        callback.onAvailable(network)
        scope.advanceUntilIdle()

        callback.onLost(network)
        scope.advanceUntilIdle()

        coVerify { primaryListener.onNetworkLost(false) }
        coVerify { secondaryListener.onNetworkLost(false) }

        callback.onLost(network)
        scope.advanceUntilIdle()

        coVerify(exactly = 1) { primaryListener.onNetworkLost(false) }
    }

    @Test
    fun `lost event for different network is ignored`() {
        val network = mockk<Network>()
        val other = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities>()
        val linkProperties = mockk<LinkProperties>()
        val snapshot = mockk<StreamNetworkInfo.Snapshot>()

        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities
        every { connectivityManager.getLinkProperties(network) } returns linkProperties
        every { snapshotBuilder.build(network, capabilities, linkProperties) } returns Result.success(snapshot)

        callback.onAvailable(network)
        scope.advanceUntilIdle()

        callback.onLost(other)
        scope.advanceUntilIdle()

        coVerify(exactly = 0) { primaryListener.onNetworkLost(any()) }

        callback.onLost(network)
        scope.advanceUntilIdle()

        coVerify(exactly = 1) { primaryListener.onNetworkLost(false) }
    }

    @Test
    fun `onUnavailable reports permanent loss once`() {
        val network = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities>()
        val linkProperties = mockk<LinkProperties>()
        val snapshot = mockk<StreamNetworkInfo.Snapshot>()

        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities
        every { connectivityManager.getLinkProperties(network) } returns linkProperties
        every { snapshotBuilder.build(network, capabilities, linkProperties) } returns Result.success(snapshot)

        callback.onAvailable(network)
        scope.advanceUntilIdle()

        callback.onUnavailable()
        scope.advanceUntilIdle()

        coVerify { primaryListener.onNetworkLost(true) }
        coVerify { secondaryListener.onNetworkLost(true) }

        callback.onUnavailable()
        scope.advanceUntilIdle()

        coVerify(exactly = 1) { primaryListener.onNetworkLost(true) }
    }

    @Test
    fun `listener failure is logged but other listeners still notified`() {
        val failingListener = primaryListener
        val healthyListener = secondaryListener
        val network = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities>()
        val linkProperties = mockk<LinkProperties>()
        val snapshot = mockk<StreamNetworkInfo.Snapshot>()
        val error = IllegalArgumentException("listener crash")

        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities
        every { connectivityManager.getLinkProperties(network) } returns linkProperties
        every { snapshotBuilder.build(network, capabilities, linkProperties) } returns Result.success(snapshot)
        coEvery { failingListener.onNetworkConnected(snapshot) } throws error
        coEvery { healthyListener.onNetworkConnected(snapshot) } just runs

        callback.onAvailable(network)
        scope.advanceUntilIdle()

        verify { logger.e(error, match { it?.invoke()?.contains("Network monitor listener failure") == true }) }
        coVerify { healthyListener.onNetworkConnected(snapshot) }
    }

    @Test
    fun `subscription iteration failure is logged`() {
        val network = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities>()
        val linkProperties = mockk<LinkProperties>()
        val snapshot = mockk<StreamNetworkInfo.Snapshot>()
        val error = IllegalStateException("iteration error")

        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities
        every { connectivityManager.getLinkProperties(network) } returns linkProperties
        every { snapshotBuilder.build(network, capabilities, linkProperties) } returns Result.success(snapshot)

        subscriptionManager.forEachFailure = error

        callback.onAvailable(network)
        scope.advanceUntilIdle()

        verify { logger.e(error, match { it?.invoke()?.contains("Failed to iterate network monitor listeners") == true }) }
        coVerify(exactly = 0) { primaryListener.onNetworkConnected(any()) }
    }

    private class RecordingSubscriptionManager : StreamSubscriptionManager<StreamNetworkMonitorListener> {
        private val listeners = linkedSetOf<StreamNetworkMonitorListener>()
        var forEachFailure: Throwable? = null

        override fun subscribe(
            listener: StreamNetworkMonitorListener,
            options: StreamSubscriptionManager.Options,
        ): Result<StreamSubscription> {
            listeners += listener
            return Result.success(object : StreamSubscription {
                private var cancelled = false
                override fun cancel() {
                    if (!cancelled) {
                        cancelled = true
                        listeners -= listener
                    }
                }
            })
        }

        override fun clear(): Result<Unit> {
            listeners.clear()
            return Result.success(Unit)
        }

        override fun forEach(block: (StreamNetworkMonitorListener) -> Unit): Result<Unit> {
            val failure = forEachFailure
            if (failure != null) {
                return Result.failure(failure)
            }
            listeners.forEach(block)
            return Result.success(Unit)
        }
    }
}
