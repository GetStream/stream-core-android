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
package io.getstream.android.core.api.model.connection.lifecycle

import io.getstream.android.core.annotations.StreamInternalApi

/**
 * Process-wide lifecycle snapshot used by the connection recovery layer.
 *
 * Implementations surface coarse-grained lifecycle boundaries (foreground/background) that
 * influence reconnection heuristics. `Unknown` is emitted while the lifecycle source is still being
 * resolved.
 */
@StreamInternalApi
public sealed class StreamLifecycleState {

    /** The lifecycle source has not yet reported a definitive state. */
    public object Unknown : StreamLifecycleState()

    /** The app is considered foregrounded (user-visible). */
    public object Foreground : StreamLifecycleState()

    /** The app moved to background and is no longer user-visible. */
    public object Background : StreamLifecycleState()
}
