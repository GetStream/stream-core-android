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

/**
 * A single unit of telemetry data captured by a [StreamTelemetryScope].
 *
 * @param tag Identifies what happened (e.g. `"connected"`, `"token.refreshed"`,
 *   `"network.changed"`).
 * @param data Arbitrary payload associated with the signal. May be `null` for tag-only signals.
 * @param timestamp Epoch milliseconds when the signal was recorded.
 */
@StreamInternalApi
public data class StreamSignal(val tag: String, val data: Any?, val timestamp: Long)
