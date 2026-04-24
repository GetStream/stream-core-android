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

package io.getstream.android.core.api.telemetry

import io.getstream.android.core.annotations.StreamInternalApi
import io.getstream.android.core.api.model.telemetry.StreamSignal

/**
 * A named channel that groups related [signals][StreamSignal].
 *
 * Scopes are created via [StreamTelemetry.scope]. Each scope maintains its own memory buffer and
 * optional disk spill. Signals flow in via [emit] and out via [drain].
 *
 * ### Threading
 *
 * All operations are thread-safe. [emit] never blocks or suspends.
 *
 * ### Example
 *
 * ```kotlin
 * val scope = telemetry.scope("connection")
 * scope.emit("connected", connectionId)
 * scope.emit("disconnected", null)
 *
 * // Later, when ready to send
 * val signals: List<StreamSignal> = scope.drain()
 * ```
 */
@StreamInternalApi
public interface StreamTelemetryScope {

    /** The name of this scope (e.g. `"connection"`, `"sfu"`, `"network"`). */
    public val name: String

    /**
     * Records a signal into this scope.
     *
     * The [StreamSignalRedactor] (if configured) runs synchronously before the signal is buffered.
     * Failures are silently consumed — this method never throws or affects the caller's flow.
     *
     * @param tag Identifies what happened.
     * @param data Optional payload. Pass `null` for tag-only signals.
     * @return [Result.success] if the signal was buffered, or [Result.failure] if an error
     *   occurred.
     */
    public fun emit(tag: String, data: Any? = null): Result<Unit>

    /**
     * Atomically consumes all buffered signals, including any that were spilled to disk.
     *
     * After this call, the scope's buffer is empty. Disk-spilled signals are read first (oldest),
     * followed by in-memory signals (newest), preserving FIFO order.
     *
     * Disk reads run on `Dispatchers.IO`.
     *
     * @return [Result.success] with signals in chronological order, or [Result.failure] if the
     *   drain could not complete (e.g. disk I/O error).
     */
    public suspend fun drain(): Result<List<StreamSignal>>
}
