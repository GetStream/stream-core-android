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
package io.getstream.android.core.internal.socket.connection

import io.getstream.android.core.api.socket.StreamConnectionIdHolder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamConnectionIdHolderImplTest {

    private fun newHolder(): StreamConnectionIdHolder = StreamConnectionIdHolderImpl()

    @Test
    fun `getConnectionId returns null initially`() {
        val holder = newHolder()

        val res = holder.getConnectionId()

        assertTrue(res.isSuccess)
        assertNull(res.getOrNull())
    }

    @Test
    fun `setConnectionId stores and returns the same value`() {
        val holder = newHolder()

        val setRes = holder.setConnectionId("conn-1")
        assertTrue(setRes.isSuccess)
        assertEquals("conn-1", setRes.getOrNull())

        val getRes = holder.getConnectionId()
        assertTrue(getRes.isSuccess)
        assertEquals("conn-1", getRes.getOrNull())
    }

    @Test
    fun `clear resets the stored value to null`() {
        val holder = newHolder()
        holder.setConnectionId("conn-2").getOrThrow()

        val clearRes = holder.clear()
        assertTrue(clearRes.isSuccess)

        val afterClear = holder.getConnectionId()
        assertTrue(afterClear.isSuccess)
        assertNull(afterClear.getOrNull())
    }

    @Test
    fun `setConnectionId overrides previous value`() {
        val holder = newHolder()
        holder.setConnectionId("old").getOrThrow()
        holder.setConnectionId("new").getOrThrow()

        val res = holder.getConnectionId()
        assertTrue(res.isSuccess)
        assertEquals("new", res.getOrNull())
    }

    @Test
    fun `setConnectionId fails on blank string`() {
        val holder = newHolder()

        val res = holder.setConnectionId("")
        assertTrue(res.isFailure)
        val ex = res.exceptionOrNull()
        assertNotNull(ex)
        assertTrue(ex is IllegalArgumentException)
        assertEquals("Connection ID cannot be blank", ex!!.message)

        // ensure value did not change
        assertNull(holder.getConnectionId().getOrThrow())
    }

    @Test
    fun `setConnectionId fails on whitespace-only string and does not overwrite existing value`() {
        val holder = newHolder()
        holder.setConnectionId("valid").getOrThrow()

        val res = holder.setConnectionId("   ")
        assertTrue(res.isFailure)
        val ex = res.exceptionOrNull()
        assertNotNull(ex)
        assertTrue(ex is IllegalArgumentException)
        assertEquals("Connection ID cannot be blank", ex!!.message)

        // previous valid value remains intact
        assertEquals("valid", holder.getConnectionId().getOrThrow())
    }

    @Test
    fun `thread safety - concurrent writers last write wins without exceptions`() {
        val holder = newHolder()
        val threads = 16
        val iterations = 200
        val pool = Executors.newFixedThreadPool(threads)
        val startGate = CountDownLatch(1)
        val doneGate = CountDownLatch(threads)

        repeat(threads) { t ->
            pool.execute {
                try {
                    startGate.await()
                    repeat(iterations) { i ->
                        // Always valid non-blank IDs
                        holder.setConnectionId("t${t}_i${i}").getOrThrow()
                    }
                } finally {
                    doneGate.countDown()
                }
            }
        }

        startGate.countDown()
        assertTrue("Workers did not finish in time", doneGate.await(5, TimeUnit.SECONDS))
        pool.shutdown()

        val finalValue = holder.getConnectionId().getOrThrow()
        assertNotNull(finalValue)
        assertTrue(finalValue!!.startsWith("t"))
    }
}
