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

import io.getstream.android.core.api.model.telemetry.StreamSignal
import io.getstream.android.core.api.telemetry.StreamSignalRedactor
import io.getstream.android.core.api.telemetry.StreamTelemetryScope
import io.getstream.android.core.api.utils.runCatchingCancellable
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Thread-safe [StreamTelemetryScope] backed by an in-memory ring buffer that spills to disk when
 * full.
 *
 * ### Buffer swap
 *
 * [emit] appends to a mutable list guarded by [synchronized]. [drain] atomically swaps the list for
 * a fresh one, so reads and writes never contend beyond the swap itself.
 *
 * ### Disk concurrency
 *
 * All disk I/O ([spillToDisk], [trimDiskIfNeeded], [drainDisk]) is serialized through a [Mutex].
 * This prevents races between a spill write and a drain read on the same file.
 *
 * ### Disk spill
 *
 * When the memory buffer exceeds the configured capacity, the oldest signals are serialized to disk
 * on [Dispatchers.IO]. If a spill is already in progress, the oldest in-memory signal is dropped
 * instead. Disk storage is capped per scope; when exceeded the oldest lines are removed in a single
 * pass.
 *
 * ### Drain order
 *
 * [drain] returns disk-spilled signals first (oldest), then in-memory signals (newest), preserving
 * FIFO order across both tiers.
 */
internal class StreamTelemetryScopeImpl(
    override val name: String,
    private val memoryCapacity: Int,
    private val diskCapacity: Long,
    private val spillDir: File,
    private val redactor: StreamSignalRedactor?,
    private val scope: CoroutineScope,
) : StreamTelemetryScope {

    private val lock = Any()
    private val diskMutex = Mutex()
    private val spillFile: File
        get() = File(spillDir, SPILL_FILE_NAME)

    private var buffer = mutableListOf<StreamSignal>()
    private val spilling = AtomicBoolean(false)

    override fun emit(tag: String, data: String?): Result<Unit> =
        // runCatching (not cancellable) — emit is not a suspend function.
        runCatching {
            val raw = StreamSignal(tag = tag, data = data, timestamp = System.currentTimeMillis())
            val signal = if (redactor != null) redactor.redact(raw) else raw
            if (signal == null) return@runCatching // redactor dropped the signal
            synchronized(lock) {
                buffer.add(signal)
                if (buffer.size > memoryCapacity) {
                    if (spilling.compareAndSet(false, true)) {
                        val snapshot = buffer
                        buffer = mutableListOf()
                        scope.launch(Dispatchers.IO) { spillToDisk(snapshot) }
                    } else {
                        buffer.removeAt(0)
                    }
                }
            }
        }

    override suspend fun drain(): Result<List<StreamSignal>> = runCatchingCancellable {
        val memorySnapshot: List<StreamSignal>
        synchronized(lock) {
            memorySnapshot = buffer
            buffer = mutableListOf()
        }
        val diskSignals = withContext(Dispatchers.IO) { drainDisk() }
        if (diskSignals.isEmpty()) {
            memorySnapshot
        } else {
            diskSignals + memorySnapshot
        }
    }

    // --- Disk spill ----------------------------------------------------------------

    private suspend fun spillToDisk(signals: List<StreamSignal>) {
        runCatchingCancellable {
            diskMutex.withLock {
                spillDir.mkdirs()
                val file = spillFile
                file.appendText(
                    signals.joinToString(separator = "\n", postfix = "\n") { encode(it) }
                )
                trimDiskIfNeeded(file)
            }
        }
        // Disk I/O failure — signals are lost. That's acceptable for telemetry.
        // Always reset the spilling flag so future emits can trigger new spills.
        spilling.set(false)
    }

    private fun trimDiskIfNeeded(file: File) {
        if (!file.exists() || file.length() <= diskCapacity) {
            return
        }
        val lines = file.readLines()
        var bytes = 0L
        var startIdx = lines.size
        for (i in lines.indices.reversed()) {
            val next = bytes + lines[i].toByteArray(Charsets.UTF_8).size + 1
            if (next > diskCapacity) break
            bytes = next
            startIdx = i
        }
        val kept = lines.subList(startIdx, lines.size)
        file.writeText(if (kept.isEmpty()) "" else kept.joinToString("\n", postfix = "\n"))
    }

    private suspend fun drainDisk(): List<StreamSignal> =
        diskMutex.withLock {
            val file = spillFile
            if (!file.exists() || file.length() == 0L) {
                return@withLock emptyList()
            }
            val signals = file.readLines().mapNotNull { decode(it) }
            file.delete()
            signals
        }

    // --- Serialization (simple line-based format) -----------------------------------

    private fun encode(signal: StreamSignal): String {
        val escapedTag = signal.tag.replace(DELIMITER, DELIMITER_ESCAPE)
        val escapedData = signal.data?.replace(DELIMITER, DELIMITER_ESCAPE).orEmpty()
        return "${signal.timestamp}$DELIMITER$escapedTag$DELIMITER$escapedData"
    }

    @Suppress("ReturnCount")
    private fun decode(line: String): StreamSignal? {
        if (line.isBlank()) {
            return null
        }
        val parts = line.split(DELIMITER, limit = 3)
        if (parts.size < 2) {
            return null
        }
        val timestamp = parts[0].toLongOrNull() ?: return null
        val tag = parts[1].replace(DELIMITER_ESCAPE, DELIMITER)
        val data = parts.getOrNull(2)?.replace(DELIMITER_ESCAPE, DELIMITER)?.ifEmpty { null }
        return StreamSignal(tag = tag, data = data, timestamp = timestamp)
    }

    private companion object {
        const val SPILL_FILE_NAME = "spill.bin"
        const val DELIMITER = "\t"
        const val DELIMITER_ESCAPE = "\\t"
    }
}
