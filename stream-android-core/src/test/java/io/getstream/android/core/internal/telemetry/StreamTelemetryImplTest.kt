/*
 * Copyright (c) 2014-2026 Stream.io Inc. All rights reserved.
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

package io.getstream.android.core.internal.telemetry

import android.content.Context
import io.getstream.android.core.api.model.config.StreamTelemetryConfig
import io.getstream.android.core.api.telemetry.StreamTelemetryNoOp
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StreamTelemetryImplTest {

    @get:Rule val tempDir = TemporaryFolder()

    private lateinit var cacheDir: File
    private lateinit var context: Context
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        cacheDir = tempDir.newFolder("cache")
        context = mockk {
            every { applicationContext } returns this
            every { this@mockk.cacheDir } returns this@StreamTelemetryImplTest.cacheDir
        }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun config(
        version: String = "1.0.0",
        root: File? = null,
        basePath: String = "stream/telemetry",
    ): StreamTelemetryConfig =
        StreamTelemetryConfig(root = root, basePath = basePath, version = version)

    // ========================================
    // Scope creation
    // ========================================

    @Test
    fun `scope creates a new scope with the given name`() {
        val sut = StreamTelemetryImpl(context, config(), scope)
        val telemetryScope = sut.scope("connection")
        assertEquals("connection", telemetryScope.name)
    }

    @Test
    fun `scope returns same instance for same name`() {
        val sut = StreamTelemetryImpl(context, config(), scope)
        val first = sut.scope("connection")
        val second = sut.scope("connection")
        assertSame(first, second)
    }

    @Test
    fun `scope returns different instances for different names`() {
        val sut = StreamTelemetryImpl(context, config(), scope)
        val connection = sut.scope("connection")
        val network = sut.scope("network")
        assertTrue(connection !== network)
        assertEquals("connection", connection.name)
        assertEquals("network", network.name)
    }

    @Test
    fun `many scopes can coexist`() {
        val sut = StreamTelemetryImpl(context, config(), scope)
        val names = (1..100).map { "scope-$it" }
        val scopes = names.map { sut.scope(it) }

        assertEquals(100, scopes.toSet().size)
        scopes.forEachIndexed { idx, s -> assertEquals(names[idx], s.name) }
    }

    @Test
    fun `scope with empty name works`() {
        val sut = StreamTelemetryImpl(context, config(), scope)
        val telemetryScope = sut.scope("")
        assertEquals("", telemetryScope.name)
    }

    @Test
    fun `scope with special characters in name works`() {
        val sut = StreamTelemetryImpl(context, config(), scope)
        val telemetryScope = sut.scope("sfu/publisher.video:h264")
        assertEquals("sfu/publisher.video:h264", telemetryScope.name)
    }

    // ========================================
    // Version cleanup
    // ========================================

    @Test
    fun `init deletes stale version directories`() = runBlocking {
        val baseDir = File(cacheDir, "stream/telemetry")
        // Create old version dirs
        File(baseDir, "0.9.0/connection").mkdirs()
        File(baseDir, "0.9.0/connection/spill.bin").writeText("old-data")
        File(baseDir, "0.8.0/network").mkdirs()
        File(baseDir, "0.8.0/network/spill.bin").writeText("older-data")

        val sut = StreamTelemetryImpl(context, config(version = "1.0.0"), scope)
        sut.cleanStaleVersions()

        assertFalse("0.9.0 should be deleted", File(baseDir, "0.9.0").exists())
        assertFalse("0.8.0 should be deleted", File(baseDir, "0.8.0").exists())
    }

    @Test
    fun `init keeps current version directory`() = runBlocking {
        val baseDir = File(cacheDir, "stream/telemetry")
        File(baseDir, "1.0.0/connection").mkdirs()
        File(baseDir, "1.0.0/connection/spill.bin").writeText("current-data")

        val sut = StreamTelemetryImpl(context, config(version = "1.0.0"), scope)
        sut.cleanStaleVersions()

        assertTrue("1.0.0 should be kept", File(baseDir, "1.0.0").exists())
        assertTrue("spill.bin should be kept", File(baseDir, "1.0.0/connection/spill.bin").exists())
    }

    @Test
    fun `cleanStaleVersions with no existing directories does not crash`() = runBlocking {
        // cacheDir exists but no telemetry dirs
        val sut = StreamTelemetryImpl(context, config(version = "1.0.0"), scope)
        val result = sut.cleanStaleVersions()
        assertTrue(result.isSuccess)
    }

    @Test
    fun `init only deletes directories under basePath, not siblings`() = runBlocking {
        // Create a file outside telemetry path
        val unrelated = File(cacheDir, "stream/other-data.txt")
        unrelated.parentFile?.mkdirs()
        unrelated.writeText("important")

        val sut = StreamTelemetryImpl(context, config(version = "1.0.0"), scope)
        sut.cleanStaleVersions()

        assertTrue("Unrelated file should survive", unrelated.exists())
    }

    @Test
    fun `cleanup only targets version directories, not files`() = runBlocking {
        val baseDir = File(cacheDir, "stream/telemetry")
        baseDir.mkdirs()
        // Create a file (not directory) in the base path
        File(baseDir, "some-file.txt").writeText("data")
        // Create an old version dir
        File(baseDir, "0.9.0").mkdirs()

        val sut = StreamTelemetryImpl(context, config(version = "1.0.0"), scope)
        sut.cleanStaleVersions()

        assertTrue("File should survive (not a directory)", File(baseDir, "some-file.txt").exists())
        assertFalse("Old version dir should be deleted", File(baseDir, "0.9.0").exists())
    }

    // ========================================
    // Custom root and basePath
    // ========================================

    @Test
    fun `custom root overrides cacheDir for cleanup`() = runBlocking {
        val customRoot = tempDir.newFolder("custom-root")
        // Seed stale version under custom root and cacheDir
        val staleUnderCustomRoot = File(customRoot, "stream/telemetry/0.9.0")
        staleUnderCustomRoot.mkdirs()
        val staleUnderCacheDir = File(cacheDir, "stream/telemetry/0.9.0")
        staleUnderCacheDir.mkdirs()

        val sut = StreamTelemetryImpl(context, config(version = "2.0.0", root = customRoot), scope)
        sut.cleanStaleVersions()

        assertFalse("Stale under custom root should be deleted", staleUnderCustomRoot.exists())
        assertTrue(
            "cacheDir should not be touched when custom root is set",
            staleUnderCacheDir.exists(),
        )
    }

    @Test
    fun `custom basePath is used`() = runBlocking {
        val baseDir = File(cacheDir, "custom/path")

        // Create old version to verify cleanup uses custom path
        File(baseDir, "0.9.0").mkdirs()

        val sut =
            StreamTelemetryImpl(context, config(version = "1.0.0", basePath = "custom/path"), scope)
        sut.cleanStaleVersions()

        assertFalse(
            "Old version under custom path should be deleted",
            File(baseDir, "0.9.0").exists(),
        )
    }

    // ========================================
    // Integration: scope + emit + drain
    // ========================================

    @Test
    fun `end-to-end emit and drain through telemetry`() = runBlocking {
        val sut = StreamTelemetryImpl(context, config(), scope)

        sut.scope("connection").emit("connected", "user-1")
        sut.scope("network").emit("wifi", "5GHz")

        val connectionSignals = sut.scope("connection").drain().getOrThrow()
        val networkSignals = sut.scope("network").drain().getOrThrow()

        assertEquals(1, connectionSignals.size)
        assertEquals("connected", connectionSignals[0].tag)

        assertEquals(1, networkSignals.size)
        assertEquals("wifi", networkSignals[0].tag)
    }

    @Test
    fun `scopes are isolated - draining one does not affect another`() = runBlocking {
        val sut = StreamTelemetryImpl(context, config(), scope)

        sut.scope("a").emit("event-a", null)
        sut.scope("b").emit("event-b", null)

        sut.scope("a").drain()

        val bSignals = sut.scope("b").drain().getOrThrow()
        assertEquals(1, bSignals.size)
        assertEquals("event-b", bSignals[0].tag)
    }

    // ========================================
    // NoOp
    // ========================================

    @Test
    fun `NoOp scope name is noop`() {
        val telemetryScope = StreamTelemetryNoOp.scope("anything")
        assertEquals("noop", telemetryScope.name)
    }

    @Test
    fun `NoOp emit does nothing`() = runBlocking {
        val telemetryScope = StreamTelemetryNoOp.scope("test")
        telemetryScope.emit("event", "data")

        val result = telemetryScope.drain()
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `NoOp returns same scope for any name`() {
        val a = StreamTelemetryNoOp.scope("a")
        val b = StreamTelemetryNoOp.scope("b")
        assertSame(a, b)
    }

    @Test
    fun `NoOp drain returns success empty list`() = runBlocking {
        val result = StreamTelemetryNoOp.scope("x").drain()
        assertTrue(result.isSuccess)
        assertEquals(emptyList<Any>(), result.getOrThrow())
    }
}
