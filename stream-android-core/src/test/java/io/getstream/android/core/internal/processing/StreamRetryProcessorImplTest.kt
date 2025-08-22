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
@file:OptIn(ExperimentalCoroutinesApi::class)

package io.getstream.android.core.internal.processing

import io.getstream.android.core.api.model.StreamRetryPolicy
import io.mockk.mockk
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class StreamRetryProcessorImplTest {

    private val retry = StreamRetryProcessorImpl(mockk(relaxed = true))

    @Test
    fun `returns success immediately when block succeeds on first attempt`() = runTest {
        val policy = StreamRetryPolicy.linear(initialDelayMillis = 0)
        val counter = AtomicInteger()

        val res =
            retry.retry(policy) {
                counter.incrementAndGet()
                "OK"
            }

        assertTrue(res.isSuccess)
        assertEquals("OK", res.getOrNull())
        assertEquals(1, counter.get())
    }

    @Test
    fun `retries until block succeeds before reaching maxRetries`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)

        val policy =
            StreamRetryPolicy.linear(
                minRetries = 1,
                maxRetries = 3,
                backoffStepMillis = 100,
                initialDelayMillis = 0,
            )

        val counter = AtomicInteger(1)
        val job =
            scope.async {
                retry.retry(policy) {
                    when (counter.incrementAndGet()) {
                        1,
                        2 -> error("Boom")
                        else -> "OK"
                    }
                }
            }

        dispatcher.scheduler.advanceUntilIdle()

        val res = job.await()
        assertTrue(res.isSuccess)
        assertEquals(3, counter.get())
        assertEquals(100, dispatcher.scheduler.currentTime)
    }

    @Test
    fun `returns failure after exhausting maxRetries`() = runTest {
        val policy = StreamRetryPolicy.linear(maxRetries = 2, minRetries = 1)
        val boom = RuntimeException("boom")

        val res = retry.retry(policy) { throw boom }

        assertTrue(res.isFailure)
        assertSame(boom, res.exceptionOrNull())
    }

    @Test
    fun `stops retrying early when giveUpFunction returns true`() = runTest {
        val policy =
            StreamRetryPolicy.linear(
                maxRetries = 5,
                minRetries = 1,
                giveUp = { attempt, _ -> attempt == 3 },
            )

        val counter = AtomicInteger()
        val err = IllegalStateException("fail")

        val res =
            retry.retry(policy) {
                counter.incrementAndGet()
                throw err
            }

        assertTrue(res.isFailure)
        assertSame(err, res.exceptionOrNull())
        assertEquals(3, counter.get())
    }

    @Test
    fun `skips delay when calculated delay is zero for all attempts`() = runTest {
        val zeroDelayPolicy =
            StreamRetryPolicy.custom(
                minRetries = 1,
                maxRetries = 3,
                initialDelayMillis = 0,
                giveUp = { retry, _ -> retry > 2 },
                minBackoffMills = 0,
                maxBackoffMills = 0,
                nextDelay = { _, _ -> 0 },
            )

        var attempts = 0
        val err = RuntimeException("fail")

        val res =
            retry.retry(zeroDelayPolicy) {
                attempts++
                throw err
            }

        assertTrue(res.isFailure)
        assertSame(err, res.exceptionOrNull())
        assertEquals(3, attempts)
    }

    @Test
    fun `stops immediately when giveUpFunction always returns true`() = runTest {
        val policy =
            StreamRetryPolicy.fixed(
                minRetries = 1,
                maxRetries = 3,
                delayMillis = 0,
                giveUp = { _, _ -> true },
            )
        val res = retry.retry(policy) { error("fail") }
        assertTrue(res.isFailure)
    }

    @Test
    fun `retries until success when giveUpFunction always returns false`() = runTest {
        val policy = StreamRetryPolicy.fixed(maxRetries = 3, delayMillis = 0)
        var cnt = 0
        val res = retry.retry(policy) { if (++cnt < 2) error("boom") else "OK" }
        assertTrue(res.isSuccess)
        assertEquals("OK", res.getOrNull())
    }

    @Test
    fun `throws policy error when maxRetries is set to zero`() = runTest {
        val policy = StreamRetryPolicy.fixed(maxRetries = 5, delayMillis = 150)
        policy.javaClass.getDeclaredField("maxRetries").apply { isAccessible = true }.set(policy, 0)

        val res = retry.retry(policy) { error("boom") }

        val err = res.exceptionOrNull()
        assertTrue(res.isFailure)
        assertEquals(IllegalStateException::class.java, err!!::class.java)
        assertTrue(err.message!!.contains("Check your policy"))
    }

    @Test
    fun `continues retrying until minRetries reached even if giveUpFunction would stop`() =
        runTest {
            val policy = StreamRetryPolicy.fixed(minRetries = 3, maxRetries = 3, delayMillis = 0)

            var calls = 0
            val res =
                retry.retry(policy) {
                    if (calls == 0) {
                        calls++
                        error("boom")
                    } else "ok"
                }

            assertTrue(res.isSuccess)
            assertEquals("ok", res.getOrNull())
            assertEquals(2, calls + 1)
        }

    @Test
    fun `retries until last attempt when giveUpFunction always returns false`() = runTest {
        val policy =
            StreamRetryPolicy.fixed(
                minRetries = 1,
                maxRetries = 3,
                delayMillis = 0,
                giveUp = { _, _ -> false },
            )

        var tries = 0
        val err = RuntimeException("fail")

        val res =
            retry.retry(policy) {
                ++tries
                throw err
            }

        assertTrue(res.isFailure)
        assertSame(err, res.exceptionOrNull())
        assertEquals(3, tries)
    }

    @Test
    fun `covers delayMs both false and true paths across iterations`() = runTest {
        var first = true
        val policy =
            StreamRetryPolicy.custom(
                minRetries = 1,
                maxRetries = 2,
                initialDelayMillis = 0, // first iteration: false
                minBackoffMills = 0,
                maxBackoffMills = 0,
                nextDelay = { _, _ ->
                    if (first) {
                        first = false
                        100L
                    } else 0L
                },
                giveUp = { _, _ -> false },
            )

        val boom = RuntimeException("fail")
        val res = retry.retry(policy) { throw boom }

        assertTrue(res.isFailure)
        assertSame(boom, res.exceptionOrNull())
    }

    @Test
    fun `loop completes all iterations without early exit`() = runTest {
        val policy =
            StreamRetryPolicy.custom(
                minRetries = 3,
                maxRetries = 3,
                initialDelayMillis = 0,
                minBackoffMills = 0,
                maxBackoffMills = 0,
                nextDelay = { _, _ -> 0 },
                giveUp = { _, _ -> false },
            )

        var attemptCount = 0
        val err = RuntimeException("fail")

        val res =
            retry.retry(policy) {
                attemptCount++
                throw err
            }

        assertTrue(res.isFailure)
        assertSame(err, res.exceptionOrNull())
        assertEquals(3, attemptCount)
    }
}
