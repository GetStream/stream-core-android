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
package io.getstream.android.core.internal.subscribe

import io.getstream.android.core.api.model.exceptions.StreamAggregateException
import io.getstream.android.core.api.subscribe.StreamSubscription
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager.Options
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager.Options.Retention
import io.mockk.mockk
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.assertSame
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private typealias Listener = () -> Unit

class StreamSubscriptionManagerImplTest {

    private fun newManager(
        maxStrong: Int = 10,
        maxWeak: Int = 10,
        strongMap: ConcurrentHashMap<Listener, StreamSubscription> = ConcurrentHashMap(),
        weakMap: MutableMap<Listener, StreamSubscription> = WeakHashMap(),
    ) =
        StreamSubscriptionManagerImpl(
            strongSubscribers = strongMap,
            weakSubscribers = weakMap,
            maxStrongSubscriptions = maxStrong,
            maxWeakSubscriptions = maxWeak,
            logger = mockk(relaxed = true),
        )

    @Test
    fun `default (AUTO_REMOVE) subscribe adds listener and forEach sees it`() {
        val manager = newManager()
        val hit = AtomicInteger()

        val sub = manager.subscribe({ hit.incrementAndGet() }).getOrThrow()
        manager.forEach { it() }.getOrThrow()

        assertEquals(1, hit.get())
        sub.cancel() // cleanup
    }

    @Test
    fun `KEEP_UNTIL_CANCELLED subscribe adds listener and forEach sees it`() {
        val manager = newManager()
        val hit = AtomicInteger()

        val sub =
            manager
                .subscribe(
                    { hit.incrementAndGet() },
                    Options(Retention.KEEP_UNTIL_CANCELLED),
                )
                .getOrThrow()

        manager.forEach { it() }.getOrThrow()
        assertEquals(1, hit.get())

        sub.cancel() // cleanup
    }

    @Test
    fun `cancel removes listener and frees strong capacity`() {
        val manager = newManager(maxStrong = 1, maxWeak = 0) // stress strong path only

        val sub =
            manager
                .subscribe({}, Options(Retention.KEEP_UNTIL_CANCELLED))
                .getOrThrow()

        // capacity reached
        assertThrows(IllegalStateException::class.java) {
            manager
                .subscribe({}, Options(Retention.KEEP_UNTIL_CANCELLED))
                .getOrThrow()
        }

        sub.cancel()

        // should succeed after cancel
        manager
            .subscribe({}, Options(Retention.KEEP_UNTIL_CANCELLED))
            .getOrThrow()
    }

    @Test
    fun `strong - subscribing the same listener twice does not double count`() {
        val manager = newManager(maxStrong = 1, maxWeak = 0)
        val listener: Listener = {}

        val r1 =
            manager.subscribe(
                listener,
                Options(Retention.KEEP_UNTIL_CANCELLED),
            )
        val r2 =
            manager.subscribe(
                listener,
                Options(Retention.KEEP_UNTIL_CANCELLED),
            )

        assertTrue(r1.isSuccess)
        assertTrue(r2.isSuccess)

        // still at capacity, a different listener should fail
        assertThrows(IllegalStateException::class.java) {
            manager
                .subscribe({}, Options(Retention.KEEP_UNTIL_CANCELLED))
                .getOrThrow()
        }
    }

    @Test
    fun `weak - subscribing the same listener twice does not double count`() {
        val manager = newManager(maxWeak = 1)

        val listener: Listener = {}
        val r1 = manager.subscribe(listener) // AUTO_REMOVE
        val r2 = manager.subscribe(listener) // same instance

        assertTrue(r1.isSuccess)
        assertTrue(r2.isSuccess)

        // different instance should hit capacity
        assertThrows(IllegalStateException::class.java) { manager.subscribe({}).getOrThrow() }
    }

    @Test
    fun `exceeding strong capacity throws`() {
        val manager = newManager(maxStrong = 2, maxWeak = 0)
        manager
            .subscribe({}, Options(Retention.KEEP_UNTIL_CANCELLED))
            .getOrThrow()
        manager
            .subscribe({}, Options(Retention.KEEP_UNTIL_CANCELLED))
            .getOrThrow()

        assertThrows(IllegalStateException::class.java) {
            manager
                .subscribe({}, Options(Retention.KEEP_UNTIL_CANCELLED))
                .getOrThrow()
        }
    }

    @Test
    fun `exceeding weak capacity throws`() {
        val manager = newManager(maxWeak = 2)
        manager.subscribe({}).getOrThrow()
        manager.subscribe({}).getOrThrow()

        assertThrows(IllegalStateException::class.java) { manager.subscribe({}).getOrThrow() }
    }

    @Test
    fun `clear removes all listeners (both weak and strong)`() {
        val manager = newManager()
        manager.subscribe({}).getOrThrow() // weak
        manager
            .subscribe({}, Options(Retention.KEEP_UNTIL_CANCELLED))
            .getOrThrow() // strong

        manager.clear().getOrThrow()

        // behaves like fresh instance
        manager.subscribe({}).getOrThrow()
        manager
            .subscribe({}, Options(Retention.KEEP_UNTIL_CANCELLED))
            .getOrThrow()
    }

    @Test
    fun `forEach accumulates failures and still invokes other listeners`() {
        val manager = newManager()
        val okHit = AtomicInteger()

        manager.subscribe({ throw IllegalStateException("boom1") }).getOrThrow()
        manager.subscribe({ throw IllegalStateException("boom2") }).getOrThrow()
        manager.subscribe({ okHit.incrementAndGet() }).getOrThrow()

        val res = manager.forEach { it() }
        assertTrue(res.isFailure)
        val ex = (res.exceptionOrNull() as StreamAggregateException)
        // exact number of failures captured from the two failing listeners
        assertEquals(2, ex.causes.size)
        assertEquals(1, okHit.get())
    }

    @Test
    fun `concurrent subscribe does not exceed strong limit`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val manager =
            StreamSubscriptionManagerImpl<Listener>(
                maxStrongSubscriptions = 50,
                maxWeakSubscriptions = 0,
                logger = mockk(relaxed = true),
            )

        val jobs =
            List(100) { i ->
                scope.async {
                    manager.subscribe(
                        { if (i == -1) println() },
                        Options(Retention.KEEP_UNTIL_CANCELLED),
                    )
                }
            }

        dispatcher.scheduler.advanceUntilIdle()
        val successes = jobs.count { it.await().isSuccess }
        assertEquals(50, successes)
    }

    @Test
    fun `concurrent strong subscribe does not exceed limit`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val manager = newManager(maxStrong = 50, maxWeak = 0)

        val jobs =
            List(100) { i ->
                scope.async {
                    manager.subscribe(
                        { if (i == -1) println() },
                        Options(Retention.KEEP_UNTIL_CANCELLED),
                    )
                }
            }

        dispatcher.scheduler.advanceUntilIdle()
        val successes = jobs.count { it.await().isSuccess }
        assertEquals(50, successes)
    }

    @Test
    fun `AUTO_REMOVE frees capacity after GC (internal peek)`() {
        val weakMap =
            java.util.Collections.synchronizedMap(
                java.util.WeakHashMap<Listener, StreamSubscription>()
            )
        val manager =
            StreamSubscriptionManagerImpl<Listener>(
                strongSubscribers = ConcurrentHashMap(),
                weakSubscribers = weakMap,
                maxStrongSubscriptions = 0,
                maxWeakSubscriptions = 5,
                logger = mockk(relaxed = true),
            )

        repeat(5) { manager.subscribe({}).getOrThrow() }

        forceGc()

        // Touch the map to expunge cleared refs before asserting
        synchronized(weakMap) { weakMap.keys.size }

        repeat(5) { manager.subscribe({}).getOrThrow() }
    }

    @Test
    fun `weak handle cancel is idempotent and safe after GC`() {
        val manager = newManager(maxWeak = 1)

        // keep a handle but drop strong ref to the listener
        var latch = CountDownLatch(1)
        var listener: Listener? = { latch.countDown() }
        val handle = manager.subscribe(listener!!).getOrThrow()

        // drop external reference & GC
        listener = null
        forceGc()

        // cancel twice should be fine (no NPE, no strong capture)
        handle.cancel()
        handle.cancel()

        // still can subscribe again (capacity freed by GC or cancel)
        manager.subscribe({}).getOrThrow()
    }

    @Test
    fun `forEach does not deadlock when subscribing inside the callback`() {
        val manager = newManager()
        val hit = AtomicInteger()

        manager.subscribe({
            hit.incrementAndGet()
            // Subscribe a new listener during iteration—should not deadlock.
            manager.subscribe({ hit.incrementAndGet() })
        })

        manager.forEach { it() }.getOrThrow()
        assertTrue(hit.get() >= 1) // at least the first listener ran; no deadlock occurred
    }

    @Test
    fun `weak subscribe returns existing handle when same listener is added again (fast path)`() {
        val manager = newManager(maxWeak = 2)
        val listener: Listener = {} // keep same instance

        val h1 = manager.subscribe(listener).getOrThrow()
        val h2 = manager.subscribe(listener).getOrThrow() // should hit outer fast-path

        assertSame(h1, h2)

        // capacity is 1 at the moment
        val res = manager.subscribe({})
        val res2 = manager.subscribe(listener) // we have this listener already
        val res3 = manager.subscribe({}) // new listener should fail

        assertTrue(res.isSuccess)
        assertTrue(res2.isSuccess)
        assertTrue(res3.isFailure)
    }

    @Test
    fun `weak subscribe returns existing handle after contention (inner recheck under lock)`() {
        val pausingWeak = PausingMap<Listener, StreamSubscription>(WeakHashMap(), onPutDelayMs = 60)
        val manager =
            newManager(
                maxStrong = 0,
                maxWeak = 1,
                weakMap = java.util.Collections.synchronizedMap(pausingWeak),
            )

        val listener: Listener = {}
        val start = CountDownLatch(2)
        val done = CountDownLatch(2)

        var r1: Result<StreamSubscription>? = null
        var r2: Result<StreamSubscription>? = null

        val t1 = Thread {
            start.countDown()
            start.await()
            r1 = manager.subscribe(listener) // will create + put (with delay)
            done.countDown()
        }
        val t2 = Thread {
            start.countDown()
            start.await()
            r2 = manager.subscribe(listener) // will hit inner fast-path under lock
            done.countDown()
        }

        t1.start()
        t2.start()
        done.await()

        val h1 = r1!!.getOrThrow()
        val h2 = r2!!.getOrThrow()
        assertSame(h1, h2)
    }

    @Test
    fun `forEach captures exception from strong subscriber`() {
        val manager =
            StreamSubscriptionManagerImpl<Listener>(
                maxStrongSubscriptions = 10,
                maxWeakSubscriptions = 0,
                logger = mockk(relaxed = true),
            )

        // one strong listener that throws
        manager
            .subscribe(
                { throw IllegalStateException("strong boom") },
                Options(Retention.KEEP_UNTIL_CANCELLED),
            )
            .getOrThrow()

        // and one strong listener that succeeds (to ensure others still run)
        val hit = AtomicInteger()
        manager
            .subscribe(
                { hit.incrementAndGet() },
                Options(Retention.KEEP_UNTIL_CANCELLED),
            )
            .getOrThrow()

        val res = manager.forEach { it() }
        assertTrue(res.isFailure) // onFailure branch taken for the throwing strong listener
        val agg = res.exceptionOrNull() as StreamAggregateException
        assertEquals(1, agg.causes.size)
        assertEquals(1, hit.get()) // the other strong listener still ran
    }

    @Test
    fun `inner recheck returns existing handle after race`() {
        // Unsynchronized weak map so the outer fast-path get does NOT take the monitor.
        val gate = GetGate()
        val weakMap = GatedGetMap<Listener, StreamSubscription>(WeakHashMap(), gate)

        val manager =
            StreamSubscriptionManagerImpl(
                strongSubscribers = ConcurrentHashMap(),
                weakSubscribers = weakMap, // <<< NOT Collections.synchronizedMap
                maxStrongSubscriptions = 0,
                maxWeakSubscriptions = 2,
                logger = mockk(relaxed = true),
            )

        val listener: Listener = {}

        var r1: Result<StreamSubscription>? = null
        var r2: Result<StreamSubscription>? = null

        // Thread B: do the outer get (returns null) and PAUSE before entering synchronized block
        val tB = thread {
            gate.pauseNextGet()
            r2 = manager.subscribe(listener) // outer get happens here and pauses
        }
        gate.awaitGetStarted() // B has executed outer get (null) and is paused

        // Thread A: inserts under synchronized(weakSubscribers)
        val tA = thread {
            r1 = manager.subscribe(listener) // creates & puts the handle
        }

        tA.join() // ensure insertion done
        gate.releaseGet() // let B continue into synchronized block
        tB.join()

        val h1 = r1!!.getOrThrow()
        val h2 = r2!!.getOrThrow()
        assertSame(h1, h2) // hits the inner 'subscription != null' branch
    }

    // ----------------- helpers -----------------
    private class PausingMap<K, V>(
        private val delegate: MutableMap<K, V>,
        private val onPutDelayMs: Long,
    ) : MutableMap<K, V> by delegate {
        override fun put(key: K, value: V): V? {
            if (onPutDelayMs > 0) Thread.sleep(onPutDelayMs)
            return delegate.put(key, value)
        }
    }

    private fun forceGc() {
        // Try a few times to increase odds of collection in CI
        repeat(5) {
            System.gc()
            System.runFinalization()
            Thread.sleep(25)
        }
    }

    private class GetGate {
        private val start = CountDownLatch(1)
        private val release = CountDownLatch(1)
        @Volatile private var armed = false

        fun pauseNextGet() {
            armed = true
        }

        fun awaitGetStarted() = start.await()

        fun releaseGet() = release.countDown()

        fun maybeGate() {
            if (armed) {
                armed = false
                start.countDown() // signal: outer get ran
                release.await() // pause until test releases
            }
        }
    }

    private class GatedGetMap<K, V>(
        private val delegate: MutableMap<K, V>,
        private val gate: GetGate,
    ) : MutableMap<K, V> by delegate {
        override fun get(key: K): V? {
            val v = delegate[key]
            gate.maybeGate()
            return v
        }
    }
}
