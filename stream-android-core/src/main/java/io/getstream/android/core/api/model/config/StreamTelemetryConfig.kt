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

package io.getstream.android.core.api.model.config

import io.getstream.android.core.annotations.StreamInternalApi
import io.getstream.android.core.api.telemetry.StreamSignalRedactor
import io.getstream.android.core.api.telemetry.StreamTelemetry
import io.getstream.android.core.api.telemetry.StreamTelemetryScope
import java.io.File

/**
 * Configuration for [StreamTelemetry].
 *
 * @param root Root directory for disk spill storage. Defaults to `context.cacheDir` (passed at
 *   creation time via the factory).
 * @param basePath Subdirectory under [root] for telemetry files (e.g. `"stream/telemetry"`).
 * @param version Version tag for the disk format — typically the core SDK version. On
 *   initialization, any directories under `{root}/{basePath}` that don't match this version are
 *   deleted.
 * @param memoryCapacity Maximum number of signals held in memory per scope before spilling to disk.
 * @param diskCapacity Maximum bytes of disk storage per scope. When exceeded, the oldest signals
 *   are dropped.
 * @param redactor Optional [StreamSignalRedactor] applied to every signal on
 *   [emit][StreamTelemetryScope.emit].
 */
@StreamInternalApi
public data class StreamTelemetryConfig(
    val root: File? = null,
    val basePath: String = "stream/telemetry",
    val version: String,
    val memoryCapacity: Int = 500,
    val diskCapacity: Long = 1_000_000L,
    val redactor: StreamSignalRedactor? = null,
)
