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

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Test

class MapUtilsTest {
    @Test
    fun `creates value when key is absent`() {
        val map = ConcurrentHashMap<String, Int>()

        val result = map.streamComputeIfAbsent("answer") { 42 }
        assertEquals(42, result)
        assertEquals(42, map["answer"])
    }

    @Test
    fun `returns existing value and does not invoke supplier`() {
        val map = ConcurrentHashMap<String, Int>().apply { put("answer", 7) }
        var supplierCalled = false

        val result =
            map.streamComputeIfAbsent("answer") {
                supplierCalled = true
                42
            }

        assertEquals(7, result, "Should return the pre-existing value")
        assertFalse("Supplier must not be executed when key already present", supplierCalled)
    }

    @Test
    fun `supplier invoked at most once under concurrency`() = runTest {
        val map = ConcurrentHashMap<String, Int>()
        val supplierCalls = AtomicInteger(0)

        // Launch 100 concurrent requests for the same key
        val jobs =
            List(100) {
                async {
                    map.streamComputeIfAbsent("k") {
                        supplierCalls.incrementAndGet()
                        99
                    }
                }
            }

        val results = jobs.map { it.await() }.toSet()

        // Every coroutine should get the same value
        assertEquals(setOf(99), results)
        // Supplier must have run exactly once
        assertEquals(1, supplierCalls.get())
        // Map must contain exactly one entry
        assertEquals(1, map.size)
    }

    @Test
    fun `streamComputeIfAbsent returns existing value when putIfAbsent reports prev`() {
        var firstCall = true

        val fakeMap =
            object : ConcurrentHashMap<String, Int>() {
                override fun putIfAbsent(key: String, value: Int): Int? =
                    if (firstCall) {
                        firstCall = false
                        // Pretend another thread just inserted 99
                        99
                    } else {
                        super.putIfAbsent(key, value)
                    }
            }

        val result = fakeMap.streamComputeIfAbsent("k") { 1 }

        assertEquals(99, result) // we got the “previous” value
        assertNull(fakeMap["k"]) // helper did NOT insert its own value
    }
}
