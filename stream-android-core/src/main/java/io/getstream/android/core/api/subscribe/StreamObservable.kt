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
package io.getstream.android.core.api.subscribe

import io.getstream.android.core.annotations.StreamPublishedApi
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager.Options

/**
 * Interface for observables that can be subscribed to.
 *
 * @param T The type of the listener.
 */
@StreamPublishedApi
public interface StreamObservable<T> {

    /**
     * Adds [listener] to the active set and returns a handle that can later be used to unregister
     * it.
     *
     * The returned [StreamSubscription] is idempotent:
     * - Calling `cancel()` multiple times is safe.
     * - After `cancel()` completes, the listener is guaranteed to be absent from subsequent
     *   [forEach] iterations.
     *
     * Retention:
     * - When [options.retention] is [Options.Retention.AUTO_REMOVE] (default), you can omit calling
     *   `cancel()`. Once your code drops all references to the listener, it is removed
     *   automatically and will no longer receive events.
     * - When [options.retention] is [Options.Retention.KEEP_UNTIL_CANCELLED], you must call
     *   `cancel()` (or invoke [clear]) to stop events.
     *
     * @param listener The listener to register.
     * @param options Retention options; defaults to automatic removal when the listener is no
     *   longer referenced.
     * @return `Result.success(StreamSubscription)` when the listener was added;
     *   `Result.failure(Throwable)` if the operation cannot be completed (e.g., capacity limits).
     */
    public fun subscribe(listener: T, options: Options = Options()): Result<StreamSubscription>
}
