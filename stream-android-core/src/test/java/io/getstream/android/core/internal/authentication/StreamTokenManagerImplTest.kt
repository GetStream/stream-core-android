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
package io.getstream.android.core.internal.authentication

import io.getstream.android.core.api.authentication.StreamTokenProvider
import io.getstream.android.core.api.model.value.StreamToken
import io.getstream.android.core.api.model.value.StreamUserId
import io.getstream.android.core.internal.processing.StreamSingleFlightProcessorImpl
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

private val UID = StreamUserId.fromString("user-1")
private val T1 = StreamToken.fromString("t1")
private val T2 = StreamToken.fromString("t2")

@OptIn(ExperimentalCoroutinesApi::class)
class StreamTokenManagerImplTest {

    @Test
    fun `loadIfAbsent returns cache without calling provider`() = runTest {
        val provider = mockk<StreamTokenProvider>(relaxed = true)
        val sf = StreamSingleFlightProcessorImpl(this)
        val mgr = StreamTokenManagerImpl(UID, provider, sf)
        mgr.setToken(T1)
        val res = mgr.loadIfAbsent()
        assertEquals(T1, res.getOrNull())
        coVerify(exactly = 0) { provider.loadToken(any()) }
    }

    @Test
    fun `loadIfAbsent triggers provider once for first call`() = runTest {
        val provider =
            mockk<StreamTokenProvider> {
                coEvery { loadToken(UID) } returns StreamToken.fromString("t1")
            }
        val sf = StreamSingleFlightProcessorImpl(this)
        val mgr = StreamTokenManagerImpl(UID, provider, sf)

        val r1 = mgr.loadIfAbsent().getOrNull()
        val r2 = mgr.loadIfAbsent().getOrNull()

        assertEquals("t1", r1?.rawValue)
        assertEquals(r1, r2)
        coVerify(exactly = 1) { provider.loadToken(UID) }
    }

    @Test
    fun `refresh bypasses cache and replaces it`() = runTest {
        val provider = mockk<StreamTokenProvider>()
        coEvery { provider.loadToken(UID) } returnsMany
            listOf("t1", "t2").map { StreamToken.fromString(it) }
        val sf = StreamSingleFlightProcessorImpl(this)
        val mgr = StreamTokenManagerImpl(UID, provider, sf)

        mgr.refresh()
        assertEquals(T1, mgr.token.value)
        mgr.refresh()

        assertEquals(T2, mgr.token.value)
        coVerify(exactly = 2) { provider.loadToken(UID) }
    }

    @Test
    fun `concurrent refresh calls coalesce`() = runTest {
        val provider = mockk<StreamTokenProvider>()
        coEvery { provider.loadToken(UID) } coAnswers
            {
                kotlinx.coroutines.delay(50)
                StreamToken.fromString("t1")
            }
        val sf = StreamSingleFlightProcessorImpl(this)
        val mgr = StreamTokenManagerImpl(UID, provider, sf)

        val r1 = async { mgr.refresh() }
        val r2 = async { mgr.refresh() }

        assertEquals(T1, r1.await().getOrNull())
        assertEquals(T1, r2.await().getOrNull())
        coVerify(exactly = 1) { provider.loadToken(UID) }
    }

    @Test
    fun `invalidate clears cache and forces reload on next access`() = runTest {
        val provider = mockk<StreamTokenProvider>()
        coEvery { provider.loadToken(UID) } returnsMany
            listOf("t1", "t2").map { StreamToken.fromString(it) }
        val sf = StreamSingleFlightProcessorImpl(this)
        val mgr = StreamTokenManagerImpl(UID, provider, sf)

        mgr.loadIfAbsent() // caches t1
        mgr.invalidate()
        assertNull(mgr.token.value)

        mgr.loadIfAbsent() // fetches again
        assertEquals(T2, mgr.token.value)
        coVerify(exactly = 2) { provider.loadToken(UID) }
    }

    @Test
    fun `setProvider replaces provider for subsequent loads`() = runTest {
        val p1 =
            mockk<StreamTokenProvider> {
                coEvery { loadToken(UID) } returns StreamToken.fromString("t1")
            }
        val p2 =
            mockk<StreamTokenProvider> {
                coEvery { loadToken(UID) } returns StreamToken.fromString("t2")
            }
        val sf = StreamSingleFlightProcessorImpl(this)
        val mgr = StreamTokenManagerImpl(UID, p1, sf)

        mgr.refresh() // uses p1
        mgr.setProvider(p2).getOrThrow()
        mgr.invalidate()
        mgr.loadIfAbsent() // should use p2 now

        coVerify(exactly = 1) { p1.loadToken(UID) }
        coVerify(exactly = 1) { p2.loadToken(UID) }
        assertEquals(T2, mgr.token.value)
    }

    @Test
    fun `loadIfAbsent waits and uses the same flight if there is a refresh ongoing`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + SupervisorJob())

        val providerCalls = AtomicInteger(0)
        val gate = CompletableDeferred<Unit>() // lets us hold the provider midway

        val provider =
            object : StreamTokenProvider {
                override suspend fun loadToken(userId: StreamUserId): StreamToken {
                    providerCalls.incrementAndGet()
                    gate.await() // suspend until test says “continue”
                    return T1
                }
            }

        val flights = StreamSingleFlightProcessorImpl(scope)
        val manager = StreamTokenManagerImpl(UID, provider, flights)

        val refreshJob = launch { manager.refresh() }

        dispatcher.scheduler.advanceUntilIdle()

        val resultDeferred = async { manager.loadIfAbsent() }

        gate.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()

        // 4) Assertions
        assertEquals(T1.rawValue, resultDeferred.await().getOrThrow().rawValue)
        assertEquals(1, providerCalls.get())
        assertTrue(refreshJob.isCompleted)
    }
}
