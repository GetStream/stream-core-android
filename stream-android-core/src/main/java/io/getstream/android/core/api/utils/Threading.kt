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

import android.os.Handler
import android.os.Looper
import io.getstream.android.core.annotations.StreamInternalApi
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Executes the given [block] on the main (UI) thread and returns the result.
 *
 * This function provides thread-safe access to the main looper with proper synchronization:
 * - If already on the main thread, executes [block] immediately
 * - If on a different thread, posts to the main looper and blocks the caller until completion
 * - Returns a [Result] that captures success values or exceptions from [block]
 *
 * ### Thread Safety
 * This function is **blocking** for the calling thread when switching threads. It uses a
 * [CountDownLatch] to wait for the main thread to complete execution before returning.
 *
 * ### Timeout
 * If the main thread does not execute [block] within **5 seconds**, this function throws
 * [IllegalStateException]. This prevents indefinite blocking if the main thread is stuck.
 *
 * ### Exception Handling
 * - Exceptions thrown by [block] are captured and returned as `Result.failure`
 * - [CancellationException][kotlin.coroutines.cancellation.CancellationException] is rethrown
 *   to preserve coroutine cancellation semantics
 * - If the main looper is not initialized, throws [IllegalStateException]
 *
 * ### Use Cases
 * - Updating UI components from background threads
 * - Adding/removing lifecycle observers (requires main thread)
 * - Accessing View properties that must be read on the main thread
 * - Synchronizing with main thread state before proceeding
 *
 * ### Example Usage
 * ```kotlin
 * // From a background thread, safely add a lifecycle observer
 * runOnMainLooper {
 *     lifecycle.addObserver(observer)
 * }.onFailure { error ->
 *     logger.e(error) { "Failed to add lifecycle observer" }
 * }
 *
 * // Get a value from the UI thread
 * val result = runOnMainLooper {
 *     view.width
 * }.getOrNull()
 *
 * // Execute multiple UI operations atomically
 * runOnMainLooper {
 *     textView.text = "Loading..."
 *     progressBar.visibility = View.VISIBLE
 * }
 * ```
 *
 * @param T the return type of [block]
 * @param block the code to execute on the main thread
 * @return [Result.success] with the return value of [block], or [Result.failure] if an exception
 *   was thrown
 * @throws IllegalStateException if the main looper is not initialized or if execution times out
 *
 * @see runOn for executing on a custom [Looper]
 */
@StreamInternalApi
public inline fun <T> runOnMainLooper(crossinline block: () -> T): Result<T> =
    runCatchingCancellable {
        val mainLooper =
            Looper.getMainLooper() ?: throw IllegalStateException("Main looper is not initialized")
        runOn(mainLooper, block).getOrThrow()
    }

/**
 * Executes the given [block] on the specified [looper]'s thread and returns the result.
 *
 * This is a generalized version of [runOnMainLooper] that works with any [Looper]:
 * - If already on the target looper's thread, executes [block] immediately
 * - If on a different thread, posts to the target looper and blocks the caller until completion
 * - Returns a [Result] that captures success values or exceptions from [block]
 *
 * ### Thread Safety
 * This function is **blocking** for the calling thread when switching threads. It uses a
 * [CountDownLatch] to wait for the target looper's thread to complete execution before returning.
 *
 * ### Timeout
 * If the target looper does not execute [block] within **5 seconds**, this function throws
 * [IllegalStateException]. This prevents indefinite blocking if the target thread is stuck
 * or the looper is not running.
 *
 * ### Exception Handling
 * - Exceptions thrown by [block] are captured and returned as `Result.failure`
 * - [CancellationException][kotlin.coroutines.cancellation.CancellationException] is rethrown
 *   to preserve coroutine cancellation semantics
 *
 * ### Use Cases
 * - Executing code on a custom [HandlerThread][android.os.HandlerThread]'s looper
 * - Synchronizing operations across multiple threads with known loopers
 * - Testing thread-specific behavior with custom test loopers
 *
 * ### Example Usage
 * ```kotlin
 * // Execute on a custom background thread
 * val handlerThread = HandlerThread("worker").apply { start() }
 * val workerLooper = handlerThread.looper
 *
 * runOn(workerLooper) {
 *     // This code runs on the worker thread
 *     performExpensiveOperation()
 * }.onSuccess { result ->
 *     println("Operation completed with result: $result")
 * }
 *
 * // Verify we're on the correct thread
 * val isCorrectThread = runOn(targetLooper) {
 *     Looper.myLooper() == targetLooper
 * }.getOrDefault(false)
 *
 * // Chain thread operations
 * runOn(backgroundLooper) {
 *     val data = fetchData()
 *     runOnMainLooper {
 *         updateUI(data)
 *     }
 * }
 * ```
 *
 * ### Performance Notes
 * - When already on the target looper's thread, there is minimal overhead (just a looper check)
 * - When switching threads, the calling thread blocks until execution completes
 * - Consider using coroutines with appropriate dispatchers for non-blocking alternatives
 *
 * @param T the return type of [block]
 * @param looper the [Looper] on whose thread to execute [block]
 * @param block the code to execute on the looper's thread
 * @return [Result.success] with the return value of [block], or [Result.failure] if an exception
 *   was thrown
 * @throws IllegalStateException if execution times out after 5 seconds
 *
 * @see runOnMainLooper for a convenience function that always uses the main looper
 */
@StreamInternalApi
public inline fun <T> runOn(looper: Looper, crossinline block: () -> T): Result<T> {
    return runCatchingCancellable {
        if (Looper.myLooper() == looper) {
            block()
        } else {
            val latch = CountDownLatch(1)
            var result: Result<T>? = null
            Handler(looper).post {
                try {
                    result = Result.success(block())
                } catch (t: Throwable) {
                    result = Result.failure(t)
                } finally {
                    latch.countDown()
                }
            }

            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw IllegalStateException("Timed out waiting to post to main thread")
            }
            result!!.getOrThrow()
        }
    }
}