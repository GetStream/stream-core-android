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

package io.getstream.android.core.api.model.retry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamRetryPolicyTest {

    // ========================================
    // Exponential Factory Function
    // ========================================

    @Test
    fun `exponential creates policy with correct defaults`() {
        val policy = StreamRetryPolicy.exponential()

        assertEquals(1, policy.minRetries)
        assertEquals(5, policy.maxRetries)
        assertEquals(250, policy.minBackoffMills)
        assertEquals(15_000, policy.maxBackoffMills)
        assertEquals(0, policy.initialDelayMillis)
    }

    @Test
    fun `exponential creates policy with custom parameters`() {
        val policy =
            StreamRetryPolicy.exponential(
                minRetries = 2,
                maxRetries = 10,
                backoffStepMillis = 500,
                maxBackoffMillis = 30_000,
                initialDelayMillis = 100,
            )

        assertEquals(2, policy.minRetries)
        assertEquals(10, policy.maxRetries)
        assertEquals(500, policy.minBackoffMills)
        assertEquals(30_000, policy.maxBackoffMills)
        assertEquals(100, policy.initialDelayMillis)
    }

    @Test
    fun `exponential backoff delay calculation increases exponentially`() {
        val policy = StreamRetryPolicy.exponential(backoffStepMillis = 100, initialDelayMillis = 0)

        // First retry: prev=0 + retry=1 * 100 = 100
        val delay1 = policy.nextBackOffDelayFunction(1, 0)
        assertEquals(100, delay1)

        // Second retry: prev=100 + retry=2 * 100 = 300
        val delay2 = policy.nextBackOffDelayFunction(2, delay1)
        assertEquals(300, delay2)

        // Third retry: prev=300 + retry=3 * 100 = 600
        val delay3 = policy.nextBackOffDelayFunction(3, delay2)
        assertEquals(600, delay3)
    }

    @Test
    fun `exponential backoff delay is capped at maxBackoffMillis`() {
        val policy =
            StreamRetryPolicy.exponential(backoffStepMillis = 1000, maxBackoffMillis = 3000)

        // Should exceed max: 0 + 10 * 1000 = 10000, but capped at 3000
        val delay = policy.nextBackOffDelayFunction(10, 0)
        assertEquals(3000, delay)
    }

    @Test
    fun `exponential backoff delay is clamped to minimum`() {
        val policy = StreamRetryPolicy.exponential(backoffStepMillis = 100)

        // Delay should never be less than backoffStepMillis
        val delay = policy.nextBackOffDelayFunction(1, 0)
        assertTrue(delay >= 100)
    }

    @Test
    fun `exponential giveUp function respects maxRetries`() {
        val policy = StreamRetryPolicy.exponential(maxRetries = 3)

        val error = RuntimeException("test")
        assertTrue(policy.giveUpFunction(4, error)) // retry 4 > maxRetries 3
    }

    // ========================================
    // Linear Factory Function
    // ========================================

    @Test
    fun `linear backoff delay increases linearly`() {
        val policy = StreamRetryPolicy.linear(backoffStepMillis = 200, initialDelayMillis = 0)

        // First retry: 0 + 200 = 200
        val delay1 = policy.nextBackOffDelayFunction(1, 0)
        assertEquals(200, delay1)

        // Second retry: 200 + 200 = 400
        val delay2 = policy.nextBackOffDelayFunction(2, delay1)
        assertEquals(400, delay2)

        // Third retry: 400 + 200 = 600
        val delay3 = policy.nextBackOffDelayFunction(3, delay2)
        assertEquals(600, delay3)
    }

    @Test
    fun `linear backoff delay is capped at maxBackoffMillis`() {
        val policy = StreamRetryPolicy.linear(backoffStepMillis = 1000, maxBackoffMillis = 2000)

        // Should exceed: 5000 + 1000 = 6000, but capped at 2000
        val delay = policy.nextBackOffDelayFunction(10, 5000)
        assertEquals(2000, delay)
    }

    // ========================================
    // Fixed Factory Function
    // ========================================

    @Test
    fun `fixed delay returns same value for all retries`() {
        val policy = StreamRetryPolicy.fixed(delayMillis = 500)

        val delay1 = policy.nextBackOffDelayFunction(1, 0)
        assertEquals(500, delay1)

        val delay2 = policy.nextBackOffDelayFunction(2, delay1)
        assertEquals(500, delay2)

        val delay3 = policy.nextBackOffDelayFunction(3, delay2)
        assertEquals(500, delay3)
    }

    @Test
    fun `fixed delay respects maxBackoffMillis in nextBackOffDelayFunction`() {
        // delayMillis must be <= maxBackoffMillis for validation to pass
        val policy = StreamRetryPolicy.fixed(delayMillis = 500, maxBackoffMillis = 1_000)

        val delay = policy.nextBackOffDelayFunction(1, 0)
        assertEquals(500, delay) // delay is less than max, so returned as-is
    }

    // ========================================
    // Custom Factory Function
    // ========================================

    @Test
    fun `custom policy respects nextDelay function`() {
        var callCount = 0
        val policy =
            StreamRetryPolicy.custom(
                minRetries = 1,
                maxRetries = 5,
                minBackoffMills = 100,
                maxBackoffMills = 5000,
                initialDelayMillis = 0,
            ) { retry, prev ->
                callCount++
                prev * 2 // Double the previous delay
            }

        val delay1 = policy.nextBackOffDelayFunction(1, 100)
        assertEquals(1, callCount)
        assertEquals(200, delay1)

        val delay2 = policy.nextBackOffDelayFunction(2, delay1)
        assertEquals(2, callCount)
        assertEquals(400, delay2)
    }

    @Test
    fun `custom policy clamps delay to minBackoffMills`() {
        val policy =
            StreamRetryPolicy.custom(
                minRetries = 1,
                maxRetries = 5,
                minBackoffMills = 500,
                maxBackoffMills = 5000,
                initialDelayMillis = 0,
            ) { _, _ ->
                100 // Return value less than min
            }

        val delay = policy.nextBackOffDelayFunction(1, 0)
        assertEquals(500, delay) // Clamped to minBackoffMills
    }

    @Test
    fun `custom policy clamps delay to maxBackoffMills`() {
        val policy =
            StreamRetryPolicy.custom(
                minRetries = 1,
                maxRetries = 5,
                minBackoffMills = 100,
                maxBackoffMills = 1000,
                initialDelayMillis = 0,
            ) { _, _ ->
                5000 // Return value greater than max
            }

        val delay = policy.nextBackOffDelayFunction(1, 0)
        assertEquals(1000, delay) // Clamped to maxBackoffMills
    }

    // ========================================
    // Validation - Require Statements
    // ========================================

    @Test(expected = IllegalArgumentException::class)
    fun `exponential throws when minRetries is negative`() {
        StreamRetryPolicy.exponential(minRetries = -1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `exponential throws when minRetries is zero`() {
        StreamRetryPolicy.exponential(minRetries = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `exponential throws when maxRetries is less than minRetries`() {
        StreamRetryPolicy.exponential(minRetries = 5, maxRetries = 3)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `exponential throws when minBackoffMills is negative`() {
        StreamRetryPolicy.exponential(backoffStepMillis = -100)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `exponential throws when maxBackoffMillis is less than minBackoffMills`() {
        StreamRetryPolicy.exponential(backoffStepMillis = 1000, maxBackoffMillis = 500)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `exponential throws when initialDelayMillis is negative`() {
        StreamRetryPolicy.exponential(initialDelayMillis = -100)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `linear throws when minRetries is zero`() {
        StreamRetryPolicy.linear(minRetries = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `linear throws when maxRetries is less than minRetries`() {
        StreamRetryPolicy.linear(minRetries = 10, maxRetries = 5)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `linear throws when minBackoffMills is negative`() {
        StreamRetryPolicy.linear(backoffStepMillis = -50)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `linear throws when maxBackoffMillis is less than minBackoffMills`() {
        StreamRetryPolicy.linear(backoffStepMillis = 2000, maxBackoffMillis = 1000)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `linear throws when initialDelayMillis is negative`() {
        StreamRetryPolicy.linear(initialDelayMillis = -500)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `fixed throws when minRetries is negative`() {
        StreamRetryPolicy.fixed(minRetries = -1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `fixed throws when maxRetries is less than minRetries`() {
        StreamRetryPolicy.fixed(minRetries = 7, maxRetries = 3)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `fixed throws when delayMillis is negative`() {
        StreamRetryPolicy.fixed(delayMillis = -100)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `fixed throws when maxBackoffMillis is less than delayMillis`() {
        StreamRetryPolicy.fixed(delayMillis = 5000, maxBackoffMillis = 1000)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `fixed throws when initialDelayMillis is negative`() {
        StreamRetryPolicy.fixed(initialDelayMillis = -200)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `custom throws when minRetries is zero`() {
        StreamRetryPolicy.custom(
            minRetries = 0,
            maxRetries = 5,
            minBackoffMills = 100,
            maxBackoffMills = 5000,
            initialDelayMillis = 0,
        ) { _, prev ->
            prev * 2
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `custom throws when maxRetries is less than minRetries`() {
        StreamRetryPolicy.custom(
            minRetries = 10,
            maxRetries = 5,
            minBackoffMills = 100,
            maxBackoffMills = 5000,
            initialDelayMillis = 0,
        ) { _, prev ->
            prev * 2
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `custom throws when minBackoffMills is negative`() {
        StreamRetryPolicy.custom(
            minRetries = 1,
            maxRetries = 5,
            minBackoffMills = -100,
            maxBackoffMills = 5000,
            initialDelayMillis = 0,
        ) { _, prev ->
            prev * 2
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `custom throws when maxBackoffMills is less than minBackoffMills`() {
        StreamRetryPolicy.custom(
            minRetries = 1,
            maxRetries = 5,
            minBackoffMills = 5000,
            maxBackoffMills = 1000,
            initialDelayMillis = 0,
        ) { _, prev ->
            prev * 2
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `custom throws when initialDelayMillis is negative`() {
        StreamRetryPolicy.custom(
            minRetries = 1,
            maxRetries = 5,
            minBackoffMills = 100,
            maxBackoffMills = 5000,
            initialDelayMillis = -100,
        ) { _, prev ->
            prev * 2
        }
    }
}
