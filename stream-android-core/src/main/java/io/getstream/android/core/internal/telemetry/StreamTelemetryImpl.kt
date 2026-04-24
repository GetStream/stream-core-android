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
import io.getstream.android.core.api.telemetry.StreamTelemetry
import io.getstream.android.core.api.telemetry.StreamTelemetryScope
import io.getstream.android.core.api.utils.runCatchingCancellable
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope

/**
 * Default [StreamTelemetry] implementation.
 *
 * Scans the telemetry base directory on [cleanStaleVersions] and deletes any version subdirectories
 * that don't match [StreamTelemetryConfig.version]. This ensures stale spill files from older SDK
 * versions are never deserialized with an incompatible format.
 */
internal class StreamTelemetryImpl(
    context: Context,
    private val config: StreamTelemetryConfig,
    private val scope: CoroutineScope,
) : StreamTelemetry {

    private val scopes = ConcurrentHashMap<String, StreamTelemetryScope>()

    private val baseDir: File =
        File(config.root ?: context.cacheDir, "${config.basePath}/${config.version}")

    override fun scope(name: String): StreamTelemetryScope =
        scopes.getOrPut(name) {
            StreamTelemetryScopeImpl(
                name = name,
                memoryCapacity = config.memoryCapacity,
                diskCapacity = config.diskCapacity,
                spillDir = File(baseDir, name),
                redactor = config.redactor,
                scope = scope,
            )
        }

    override suspend fun cleanStaleVersions(): Result<Unit> = runCatchingCancellable {
        val versionParent = baseDir.parentFile ?: return@runCatchingCancellable
        if (!versionParent.exists()) {
            return@runCatchingCancellable
        }
        versionParent.listFiles()?.forEach { dir ->
            if (dir.isDirectory && dir.name != config.version) {
                dir.deleteRecursively()
            }
        }
    }
}
