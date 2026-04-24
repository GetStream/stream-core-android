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
import io.getstream.android.core.api.telemetry.StreamTelemetry
import io.getstream.android.core.api.telemetry.StreamTelemetryConfig
import io.getstream.android.core.api.telemetry.StreamTelemetryScope
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Default [StreamTelemetry] implementation.
 *
 * On creation, scans the telemetry base directory and deletes any version subdirectories that don't
 * match [StreamTelemetryConfig.version]. This ensures stale spill files from older SDK versions are
 * never deserialized with an incompatible format.
 */
internal class StreamTelemetryImpl(context: Context, private val config: StreamTelemetryConfig) :
    StreamTelemetry {

    private val scopes = ConcurrentHashMap<String, StreamTelemetryScope>()

    private val baseDir: File =
        File(config.root ?: context.cacheDir, "${config.basePath}/${config.version}")

    init {
        cleanStaleVersions()
    }

    override fun scope(name: String): StreamTelemetryScope =
        scopes.getOrPut(name) {
            StreamTelemetryScopeImpl(
                name = name,
                memoryCapacity = config.memoryCapacity,
                diskCapacity = config.diskCapacity,
                spillDir = File(baseDir, name),
                redactor = config.redactor,
            )
        }

    /**
     * Deletes any sibling directories under the telemetry base path that don't match the current
     * version. For example, if the base path is `stream/telemetry` and the version is `1.2.0`, this
     * deletes `stream/telemetry/1.1.0/` but keeps `stream/telemetry/1.2.0/`.
     */
    @Suppress("ReturnCount")
    private fun cleanStaleVersions() {
        try {
            val versionParent = baseDir.parentFile ?: return
            if (!versionParent.exists()) {
                return
            }
            versionParent.listFiles()?.forEach { dir ->
                if (dir.isDirectory && dir.name != config.version) {
                    dir.deleteRecursively()
                }
            }
        } catch (@Suppress("TooGenericExceptionCaught") ignored: Exception) {
            // Best-effort cleanup. If it fails, stale files remain in cache — OS can reclaim.
        }
    }
}
