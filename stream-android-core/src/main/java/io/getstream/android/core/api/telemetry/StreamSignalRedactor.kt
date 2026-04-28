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
 * Transforms or redacts sensitive data from a [StreamSignal] before it is stored.
 *
 * Redactors run synchronously on [StreamTelemetryScope.emit] — the returned signal is what gets
 * buffered. Return `null` to drop the signal entirely.
 *
 * ### Example
 *
 * ```kotlin
 * val redactor = StreamSignalRedactor { signal ->
 *     if (signal.tag == "auth.token") {
 *         signal.copy(data = "[REDACTED]")
 *     } else {
 *         signal
 *     }
 * }
 * ```
 */
@StreamInternalApi
public fun interface StreamSignalRedactor {

    /**
     * Transforms or redacts the given [signal].
     *
     * @param signal The signal to process.
     * @return The redacted signal, or `null` to drop it.
     */
    public fun redact(signal: StreamSignal): StreamSignal?
}
