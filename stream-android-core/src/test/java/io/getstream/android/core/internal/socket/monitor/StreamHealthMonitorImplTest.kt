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
package io.getstream.android.core.internal.socket.monitor

import io.getstream.android.core.api.log.StreamLogger
import io.mockk.mockk
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class StreamHealthMonitorImplTest {
    class FakeClock(startMillis: Long = 0L) : Clock {
        private var nowMillis: Long = startMillis

        fun advanceBy(millis: Long) {
            nowMillis += millis
        }

        override fun now(): Instant = Instant.fromEpochMilliseconds(nowMillis)
    }

    @Test
    fun `onHeartbeat callback runs when within threshold`() = runTest {
        var heartbeatCalled = false
        val monitor =
            StreamHealthMonitorImpl(
                logger = mockk(relaxed = true),
                scope = backgroundScope,
                interval = 100,
                livenessThreshold = 200,
                clock = Clock.System,
            )

        monitor.onHeartbeat { heartbeatCalled = true }
        monitor.onUnhealthy { error("should not be called") }

        monitor.start()

        // Advance just past the interval, not exactly equal
        advanceTimeBy(101)
        advanceUntilIdle()

        assertTrue(heartbeatCalled)
    }

    @Test
    fun `onUnhealthy callback runs after threshold`() = runTest {
        val fakeClock = FakeClock(0)
        var unhealthyCalled = false

        val monitor =
            StreamHealthMonitorImpl(
                logger = mockk(relaxed = true),
                scope = backgroundScope,
                interval = 100,
                livenessThreshold = 200,
                clock = fakeClock,
            )

        monitor.onUnhealthy { unhealthyCalled = true }
        monitor.onHeartbeat {}
        monitor.start()

        // First tick (100ms virtual time, 0ms fake clock) → heartbeat
        advanceTimeBy(100)
        advanceUntilIdle()
        assertFalse(unhealthyCalled)

        // Move fake time past threshold
        fakeClock.advanceBy(300)

        // Next tick, still at 200ms delay
        advanceTimeBy(100)
        advanceUntilIdle()

        assertTrue(unhealthyCalled)
    }

    @Test
    fun `acknowledgeHeartbeat resets lastAck so healthy again`() = runTest {
        val monitor =
            StreamHealthMonitorImpl(
                logger = mockk<StreamLogger>(relaxed = true),
                scope = backgroundScope,
                interval = 100, // make tiny for test speed
                livenessThreshold = 200,
            )
        var heartbeatCount = 0
        var unhealthyCalled = false
        monitor.onHeartbeat { heartbeatCount++ }
        monitor.onUnhealthy { unhealthyCalled = true }

        monitor.start()

        // First interval: heartbeat
        advanceTimeBy(100)
        advanceUntilIdle()

        // Now push near threshold
        advanceTimeBy(150)
        // Acknowledge just before threshold
        monitor.acknowledgeHeartbeat()

        // Advance again, expect heartbeat not unhealthy
        advanceTimeBy(100)
        advanceUntilIdle()

        assertTrue(heartbeatCount >= 2)
        assertTrue(!unhealthyCalled)
    }

    @Test
    fun `start twice only creates one job`() = runTest {
        val monitor =
            StreamHealthMonitorImpl(
                logger = mockk<StreamLogger>(relaxed = true),
                scope = backgroundScope,
                interval = 100, // make tiny for test speed
                livenessThreshold = 200,
            )
        monitor.start()
        val job1 = getJob(monitor)

        monitor.start()
        val job2 = getJob(monitor)

        assertTrue(job1 === job2) // same instance
        assertTrue(job1!!.isActive)
    }

    @Test
    fun `start restarts when monitorJob exists but is not active`() = runTest {
        val logger = mockk<StreamLogger>(relaxed = true)
        val monitor =
            StreamHealthMonitorImpl(
                logger = logger,
                scope = backgroundScope,
                interval = 50,
                livenessThreshold = 100,
            )

        // Start once → monitorJob created
        monitor.start()
        val jobField =
            monitor.javaClass.getDeclaredField("monitorJob").apply { isAccessible = true }
        val firstJob = jobField.get(monitor) as Job

        // Stop it → cancels the job, so !isActive but not null
        monitor.stop()
        assertFalse(firstJob.isActive)
        assertNotNull(jobField.get(monitor))

        // Restart should create a NEW active job
        monitor.start()
        val secondJob = jobField.get(monitor) as Job
        assertTrue(secondJob.isActive)
        assertNotSame(firstJob, secondJob)
    }

    @Test
    fun `stop cancels job`() = runTest {
        val monitor =
            StreamHealthMonitorImpl(
                logger = mockk<StreamLogger>(relaxed = true),
                scope = backgroundScope,
                interval = 100, // make tiny for test speed
                livenessThreshold = 200,
            )
        monitor.start()
        val job = getJob(monitor)!!
        assertTrue(job.isActive)

        monitor.stop()
        assertTrue(!job.isActive)
    }

    @Test
    fun `start does nothing if monitorJob is already active`() = runTest {
        val logger = mockk<StreamLogger>(relaxed = true)
        val monitor =
            StreamHealthMonitorImpl(
                logger = logger,
                scope = backgroundScope,
                interval = 50,
                livenessThreshold = 100,
            )

        monitor.start()
        val firstJob =
            monitor.javaClass
                .getDeclaredField("monitorJob")
                .apply { isAccessible = true }
                .get(monitor)

        monitor.start() // second time should no-op

        val secondJob =
            monitor.javaClass
                .getDeclaredField("monitorJob")
                .apply { isAccessible = true }
                .get(monitor)

        // Same job instance, not restarted
        assertSame(firstJob, secondJob)
    }

    @Test
    fun `stop does nothing gracefully if monitorJob is null`() {
        val logger = mockk<StreamLogger>(relaxed = true)
        val monitor =
            StreamHealthMonitorImpl(
                logger = logger,
                scope = CoroutineScope(Job()), // any scope
                interval = 50,
                livenessThreshold = 100,
            )

        // No start() => monitorJob is null
        monitor.stop()

        val jobField = monitor.javaClass.getDeclaredField("monitorJob")
        jobField.isAccessible = true
        val job = jobField.get(monitor) as Job?
        assertTrue(job == null)
    }

    @Test
    fun `monitor loop exits immediately when job is cancelled before first delay`() = runTest {
        val logger = mockk<StreamLogger>(relaxed = true)
        val monitor =
            StreamHealthMonitorImpl(
                logger = logger,
                scope = backgroundScope,
                interval = 100,
                livenessThreshold = 200,
            )

        monitor.onHeartbeat { error("Should not be called") }
        monitor.onUnhealthy { error("Should not be called") }

        // Start the monitor, job is created but suspended at delay()
        monitor.start()

        val jobField =
            monitor.javaClass.getDeclaredField("monitorJob").apply { isAccessible = true }
        val job = jobField.get(monitor) as Job

        // Cancel the job before advancing time so that `isActive == false`
        job.cancel()

        // Pump the scheduler to let coroutine start and hit the while condition
        testScheduler.runCurrent()

        // Advance time further just to be sure callbacks would have fired if loop continued
        advanceTimeBy(500)
        advanceUntilIdle()
    }

    // helper to reflectively access monitorJob
    private fun getJob(monitor: StreamHealthMonitorImpl): Job? {
        val field = StreamHealthMonitorImpl::class.java.getDeclaredField("monitorJob")
        field.isAccessible = true
        return field.get(monitor) as Job?
    }
}
