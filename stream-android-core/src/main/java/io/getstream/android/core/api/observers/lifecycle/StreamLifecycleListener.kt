/*
 * Copyright (c) 2014-2025 Stream.io Inc. All rights reserved.
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

package io.getstream.android.core.api.observers.lifecycle

import io.getstream.android.core.annotations.StreamInternalApi

/**
 * Callbacks mirroring host lifecycle events emitted by [StreamLifecycleMonitor].
 *
 * Implementers typically register via `StreamLifecycleMonitor.subscribe(listener)` and override the
 * relevant callbacks to react to lifecycle transitions.
 */
@StreamInternalApi
public interface StreamLifecycleListener {

    /**
     * Called when the app moves to the foreground.
     *
     * ### Example
     *
     * ```kotlin
     * override fun onForeground() {
     *     logger.i { "App moved to foreground" }
     * }
     * ```
     */
    public fun onForeground() {}

    /**
     * Called when the app moves to the background.
     *
     * ### Example
     *
     * ```kotlin
     * override fun onBackground() {
     *     logger.i { "App moved to background" }
     * }
     * ```
     */
    public fun onBackground() {}
}
