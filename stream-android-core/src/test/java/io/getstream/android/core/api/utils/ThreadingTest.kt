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

package io.getstream.android.core.api.utils

import android.os.Build
import android.os.HandlerThread
import android.os.Looper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class ThreadingTest {

    // ========== runOnMainLooper tests ==========

    @Test
    fun `runOnMainLooper returns success when block succeeds`() {
        val result = runOnMainLooper { 42 }

        assertTrue(result.isSuccess)
        assertEquals(42, result.getOrNull())
    }

    @Test
    fun `runOnMainLooper returns success with Unit`() {
        var executed = false
        val result = runOnMainLooper { executed = true }

        assertTrue(result.isSuccess)
        assertTrue(executed)
    }

    @Test
    fun `runOnMainLooper returns success with nullable value`() {
        val result = runOnMainLooper<String?> { null }

        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
    }

    @Test
    fun `runOnMainLooper captures exceptions as failure`() {
        val exception = IllegalStateException("test error")
        val result = runOnMainLooper<Int> { throw exception }

        assertTrue(result.isFailure)
        assertSame(exception, result.exceptionOrNull())
    }

    @Test
    fun `runOnMainLooper rethrows CancellationException`() {
        assertFailsWith<CancellationException> {
            runOnMainLooper<Int> { throw CancellationException("cancelled") }
        }
    }

    @Test
    fun `runOnMainLooper executes on main thread when already on main thread`() {
        val executionThread = AtomicReference<Thread?>()

        val result = runOnMainLooper {
            executionThread.set(Thread.currentThread())
            "success"
        }

        assertTrue(result.isSuccess)
        assertEquals(Looper.getMainLooper().thread, executionThread.get())
    }

    @Test
    fun `runOnMainLooper posts to main thread from background thread`() {
        val mainLooper = Looper.getMainLooper()
        val shadowLooper = Shadows.shadowOf(mainLooper)
        val executionThread = AtomicReference<Thread?>()
        val completed = CountDownLatch(1)

        thread(start = true, name = "ThreadingTest-worker") {
            val result = runOnMainLooper {
                executionThread.set(Thread.currentThread())
                "from-main"
            }

            assertTrue(result.isSuccess)
            assertEquals("from-main", result.getOrNull())
            completed.countDown()
        }

        // Process main looper tasks while waiting for thread to complete
        while (!completed.await(10, TimeUnit.MILLISECONDS)) {
            shadowLooper.idle()
        }

        assertEquals(mainLooper.thread, executionThread.get())
    }

    @Test
    fun `runOnMainLooper executes block exactly once on success`() {
        val callCount = AtomicInteger(0)

        val result = runOnMainLooper { callCount.incrementAndGet() }

        assertTrue(result.isSuccess)
        assertEquals(1, callCount.get())
    }

    @Test
    fun `runOnMainLooper executes block exactly once on failure`() {
        val callCount = AtomicInteger(0)
        val exception = RuntimeException("error")

        val result =
            runOnMainLooper<Unit> {
                callCount.incrementAndGet()
                throw exception
            }

        assertTrue(result.isFailure)
        assertSame(exception, result.exceptionOrNull())
        assertEquals(1, callCount.get())
    }

    @Test
    fun `runOnMainLooper handles multiple concurrent calls from background threads`() {
        val mainLooper = Looper.getMainLooper()
        val shadowLooper = Shadows.shadowOf(mainLooper)
        val results = mutableListOf<Int>()
        val completed = CountDownLatch(5)

        repeat(5) { index ->
            thread(start = true, name = "ThreadingTest-worker-$index") {
                val result = runOnMainLooper { index }
                synchronized(results) { results.add(result.getOrThrow()) }
                completed.countDown()
            }
        }

        // Process main looper tasks while waiting for threads to complete
        while (!completed.await(10, TimeUnit.MILLISECONDS)) {
            shadowLooper.idle()
        }

        assertEquals(5, results.size)
        assertTrue(results.containsAll(listOf(0, 1, 2, 3, 4)))
    }

    @Test
    fun `runOnMainLooper returns complex objects`() {
        data class ComplexObject(val id: Int, val name: String, val items: List<String>)

        val expected = ComplexObject(42, "test", listOf("a", "b", "c"))
        val result = runOnMainLooper { expected }

        assertTrue(result.isSuccess)
        assertEquals(expected, result.getOrNull())
    }

    @Test
    fun `runOnMainLooper can be nested`() {
        val result = runOnMainLooper {
            val inner = runOnMainLooper { "inner" }
            "outer-${inner.getOrNull()}"
        }

        assertTrue(result.isSuccess)
        assertEquals("outer-inner", result.getOrNull())
    }

    // ========== runOn tests ==========

    @Test
    fun `runOn returns success when block succeeds`() {
        val mainLooper = Looper.getMainLooper()
        val result = runOn(mainLooper) { 42 }

        assertTrue(result.isSuccess)
        assertEquals(42, result.getOrNull())
    }

    @Test
    fun `runOn executes immediately when already on target looper`() {
        val mainLooper = Looper.getMainLooper()
        val executionThread = AtomicReference<Thread?>()

        val result =
            runOn(mainLooper) {
                executionThread.set(Thread.currentThread())
                Looper.myLooper()
            }

        assertTrue(result.isSuccess)
        assertEquals(mainLooper, result.getOrNull())
        assertEquals(mainLooper.thread, executionThread.get())
    }

    @Test
    fun `runOn posts to target looper from different thread`() {
        val mainLooper = Looper.getMainLooper()
        val shadowLooper = Shadows.shadowOf(mainLooper)
        val executionThread = AtomicReference<Thread?>()
        val completed = CountDownLatch(1)

        thread(start = true, name = "ThreadingTest-worker") {
            val result =
                runOn(mainLooper) {
                    executionThread.set(Thread.currentThread())
                    "success"
                }

            assertTrue(result.isSuccess)
            assertEquals("success", result.getOrNull())
            completed.countDown()
        }

        // Process main looper tasks while waiting for thread to complete
        while (!completed.await(10, TimeUnit.MILLISECONDS)) {
            shadowLooper.idle()
        }

        assertEquals(mainLooper.thread, executionThread.get())
    }

    @Test
    fun `runOn works with custom HandlerThread looper`() {
        val handlerThread = HandlerThread("ThreadingTest-custom").apply { start() }
        try {
            val customLooper = handlerThread.looper
            val executionThread = AtomicReference<Thread?>()
            val completed = CountDownLatch(1)

            thread(start = true, name = "ThreadingTest-caller") {
                val result =
                    runOn(customLooper) {
                        executionThread.set(Thread.currentThread())
                        Looper.myLooper()
                    }

                assertTrue(result.isSuccess)
                assertEquals(customLooper, result.getOrNull())
                completed.countDown()
            }

            assertTrue(completed.await(2, TimeUnit.SECONDS))
            assertEquals(customLooper.thread, executionThread.get())
        } finally {
            handlerThread.quitSafely()
        }
    }

    @Test
    fun `runOn captures exceptions from target looper`() {
        val mainLooper = Looper.getMainLooper()
        val shadowLooper = Shadows.shadowOf(mainLooper)
        val exception = IllegalArgumentException("test error")
        val completed = CountDownLatch(1)

        thread(start = true, name = "ThreadingTest-worker") {
            val result = runOn(mainLooper) { throw exception }

            assertTrue(result.isFailure)
            assertSame(exception, result.exceptionOrNull())
            completed.countDown()
        }

        // Process main looper tasks while waiting for thread to complete
        while (!completed.await(10, TimeUnit.MILLISECONDS)) {
            shadowLooper.idle()
        }
    }

    @Test
    fun `runOn rethrows CancellationException`() {
        val mainLooper = Looper.getMainLooper()

        assertFailsWith<CancellationException> {
            runOn(mainLooper) { throw CancellationException("cancelled") }
        }
    }

    @Test
    fun `runOn executes block exactly once`() {
        val mainLooper = Looper.getMainLooper()
        val callCount = AtomicInteger(0)

        val result =
            runOn(mainLooper) {
                callCount.incrementAndGet()
                "done"
            }

        assertTrue(result.isSuccess)
        assertEquals("done", result.getOrNull())
        assertEquals(1, callCount.get())
    }

    @Test
    fun `runOn handles multiple sequential calls`() {
        val handlerThread = HandlerThread("ThreadingTest-sequential").apply { start() }
        try {
            val customLooper = handlerThread.looper
            val results = mutableListOf<Int>()

            repeat(10) { index ->
                val result = runOn(customLooper) { index }
                results.add(result.getOrThrow())
            }

            assertEquals(List(10) { it }, results)
        } finally {
            handlerThread.quitSafely()
        }
    }

    @Test
    fun `runOn from main looper to background looper`() {
        val handlerThread = HandlerThread("ThreadingTest-background").apply { start() }
        try {
            val backgroundLooper = handlerThread.looper
            val mainThread = Thread.currentThread()
            val executionThread = AtomicReference<Thread?>()

            val result =
                runOn(backgroundLooper) {
                    executionThread.set(Thread.currentThread())
                    "from-background"
                }

            assertTrue(result.isSuccess)
            assertEquals("from-background", result.getOrNull())
            assertNotNull(executionThread.get())
            assertTrue(mainThread != executionThread.get())
        } finally {
            handlerThread.quitSafely()
        }
    }

    @Test
    fun `runOn can switch between multiple custom loopers`() {
        val thread1 = HandlerThread("ThreadingTest-thread1").apply { start() }
        val thread2 = HandlerThread("ThreadingTest-thread2").apply { start() }

        try {
            val looper1 = thread1.looper
            val looper2 = thread2.looper

            val result1 =
                runOn(looper1) {
                    val result2 = runOn(looper2) { Looper.myLooper() }
                    result2.getOrThrow() to Looper.myLooper()
                }

            assertTrue(result1.isSuccess)
            val (innerLooper, outerLooper) = result1.getOrThrow()
            assertEquals(looper2, innerLooper)
            assertEquals(looper1, outerLooper)
        } finally {
            thread1.quitSafely()
            thread2.quitSafely()
        }
    }

    @Test
    fun `runOn propagates return value correctly`() {
        data class Result(val success: Boolean, val message: String, val count: Int)

        val mainLooper = Looper.getMainLooper()
        val expected = Result(success = true, message = "test", count = 42)

        val result = runOn(mainLooper) { expected }

        assertTrue(result.isSuccess)
        assertEquals(expected, result.getOrNull())
    }

    @Test
    fun `runOn handles exceptions thrown in nested calls`() {
        val handlerThread = HandlerThread("ThreadingTest-nested").apply { start() }
        try {
            val customLooper = handlerThread.looper
            val mainLooper = Looper.getMainLooper()
            val shadowLooper = Shadows.shadowOf(mainLooper)
            val exception = RuntimeException("inner error")
            val completed = CountDownLatch(1)
            val resultRef = AtomicReference<Result<Unit>?>()

            thread(start = true, name = "ThreadingTest-nested-worker") {
                val result =
                    runOn(customLooper) { runOn(mainLooper) { throw exception }.getOrThrow() }
                resultRef.set(result)
                completed.countDown()
            }

            // Process main looper tasks while waiting for thread to complete
            while (!completed.await(10, TimeUnit.MILLISECONDS)) {
                shadowLooper.idle()
            }

            val result = resultRef.get()
            assertNotNull(result)
            assertTrue(result.isFailure)
            val cause = result.exceptionOrNull()
            assertSame(exception, cause)
        } finally {
            handlerThread.quitSafely()
        }
    }

    @Test
    fun `runOn with stopped HandlerThread eventually times out`() {
        val handlerThread = HandlerThread("ThreadingTest-stopped").apply { start() }
        val stoppedLooper = handlerThread.looper
        handlerThread.quitSafely()
        handlerThread.join(1000)

        // This should timeout because the looper is no longer processing messages
        val exception =
            assertFailsWith<IllegalStateException> {
                runOn(stoppedLooper) { "should-not-execute" }.getOrThrow()
            }

        assertTrue(exception.message?.contains("Timed out") == true)
    }

    @Test
    fun `runOn handles concurrent calls to same looper`() {
        val handlerThread = HandlerThread("ThreadingTest-concurrent").apply { start() }
        try {
            val customLooper = handlerThread.looper
            val results = mutableListOf<Int>()
            val completed = CountDownLatch(10)

            repeat(10) { index ->
                thread(start = true, name = "ThreadingTest-worker-$index") {
                    val result = runOn(customLooper) { index }
                    synchronized(results) { results.add(result.getOrThrow()) }
                    completed.countDown()
                }
            }

            assertTrue(completed.await(5, TimeUnit.SECONDS))
            assertEquals(10, results.size)
            assertTrue(results.containsAll(List(10) { it }))
        } finally {
            handlerThread.quitSafely()
        }
    }

    @Test
    fun `runOn preserves exception stack trace`() {
        val mainLooper = Looper.getMainLooper()
        val exception = IllegalStateException("custom error")

        val result = runOn(mainLooper) { throw exception }

        assertTrue(result.isFailure)
        val caught = result.exceptionOrNull()
        assertSame(exception, caught)
        assertNotNull(caught?.stackTrace)
        assertTrue(caught?.stackTrace?.isNotEmpty() == true)
    }

    // ========== Integration tests ==========

    @Test
    fun `runOnMainLooper and runOn can be used together`() {
        val handlerThread = HandlerThread("ThreadingTest-integration").apply { start() }
        try {
            val customLooper = handlerThread.looper
            val mainLooper = Looper.getMainLooper()
            val shadowLooper = Shadows.shadowOf(mainLooper)
            val mainThread = mainLooper.thread
            val completed = CountDownLatch(1)
            val resultRef = AtomicReference<Pair<Thread, Thread>?>()

            thread(start = true, name = "ThreadingTest-integration-worker") {
                val result =
                    runOn(customLooper) {
                        val mainResult = runOnMainLooper { Thread.currentThread() }
                        mainResult.getOrThrow() to Thread.currentThread()
                    }
                resultRef.set(result.getOrThrow())
                completed.countDown()
            }

            // Process main looper tasks while waiting for thread to complete
            while (!completed.await(10, TimeUnit.MILLISECONDS)) {
                shadowLooper.idle()
            }

            val (mainThreadFromInner, customThread) = resultRef.get()!!
            assertEquals(mainThread, mainThreadFromInner)
            assertTrue(mainThread != customThread)
        } finally {
            handlerThread.quitSafely()
        }
    }

    @Test
    fun `stress test - many rapid calls from different threads`() {
        val mainLooper = Looper.getMainLooper()
        val shadowLooper = Shadows.shadowOf(mainLooper)
        val count = 50
        val completed = CountDownLatch(count)
        val results = mutableListOf<Int>()

        repeat(count) { index ->
            thread(start = true, name = "ThreadingTest-stress-$index") {
                val result = runOnMainLooper { index }
                synchronized(results) { results.add(result.getOrThrow()) }
                completed.countDown()
            }
        }

        // Process main looper tasks while waiting for threads to complete
        while (!completed.await(10, TimeUnit.MILLISECONDS)) {
            shadowLooper.idle()
        }

        assertEquals(count, results.size)
        assertTrue(results.containsAll(List(count) { it }))
    }
}
