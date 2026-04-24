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

import android.content.Context
import io.getstream.android.core.annotations.StreamInternalApi
import io.getstream.android.core.api.model.config.StreamTelemetryConfig
import io.getstream.android.core.api.model.telemetry.StreamSignal
import io.getstream.android.core.internal.telemetry.StreamTelemetryImpl
import kotlinx.coroutines.CoroutineScope

/**
 * Engine for collecting telemetry signals across named [scopes][StreamTelemetryScope].
 *
 * Product SDKs create an instance via the [StreamTelemetry] factory, inject it into
 * [StreamComponentProvider][io.getstream.android.core.api.model.config.StreamComponentProvider],
 * and retain a reference for draining. Core emits signals internally; the product SDK decides when
 * and where to send them.
 *
 * If no telemetry is provided, core uses [StreamTelemetryNoOp] which discards everything at zero
 * cost.
 *
 * ### Usage
 *
 * ```kotlin
 * // Product SDK creates and keeps a reference
 * val telemetry = StreamTelemetry(context, config)
 *
 * // Inject into core
 * val client = StreamClient(
 *     ...,
 *     components = StreamComponentProvider(telemetry = telemetry),
 * )
 *
 * // Core emits internally
 * telemetry.scope("connection").emit("connected", connectionId)
 *
 * // Product SDK drains on its own schedule
 * val signals = telemetry.scope("connection").drain()
 * ```
 */
@StreamInternalApi
public interface StreamTelemetry {

    /**
     * Returns the [StreamTelemetryScope] for the given [name], creating it if it doesn't exist.
     *
     * Scope names are open — any string is valid. Core uses names like `"connection"`, `"network"`,
     * `"auth"`, and `"device"`. Product SDKs add their own (e.g. `"sfu"`, `"publisher"`).
     *
     * @param name Scope identifier.
     * @return The scope for [name].
     */
    public fun scope(name: String): StreamTelemetryScope
}

/**
 * Creates a [StreamTelemetry] instance backed by the default implementation.
 *
 * @param context Android application context (used for `cacheDir` when [StreamTelemetryConfig.root]
 *   is `null`).
 * @param config Telemetry configuration.
 * @param scope Coroutine scope for disk I/O operations (spill, drain, cleanup).
 * @return A new [StreamTelemetry] instance.
 */
@StreamInternalApi
public fun StreamTelemetry(
    context: Context,
    config: StreamTelemetryConfig,
    scope: CoroutineScope,
): StreamTelemetry = StreamTelemetryImpl(context.applicationContext, config, scope)

/**
 * No-op implementation that discards all signals without allocating buffers.
 *
 * Used internally when no telemetry is provided via
 * [StreamComponentProvider][io.getstream.android.core.api.model.config.StreamComponentProvider].
 */
@StreamInternalApi
public object StreamTelemetryNoOp : StreamTelemetry {

    override fun scope(name: String): StreamTelemetryScope = NoOpScope

    private object NoOpScope : StreamTelemetryScope {
        override val name: String = "noop"

        override fun emit(tag: String, data: Any?): Result<Unit> = Result.success(Unit)

        override suspend fun drain(): Result<List<StreamSignal>> = Result.success(emptyList())
    }
}
