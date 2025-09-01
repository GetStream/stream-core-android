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

import io.getstream.android.core.annotations.StreamInternalApi
import io.getstream.android.core.api.log.StreamLogger
import io.getstream.android.core.internal.subscribe.StreamSubscriptionManagerImpl

/**
 * Registry that owns a set of listeners and provides minimal lifecycle control over them.
 *
 * Typical usage:
 * ```kotlin
 * private val subs = StreamSubscriptionManagerImpl<(Event) -> Unit>()
 *
 * fun onSomethingHappened(event: Event) {
 *     subs.forEach { listener -> listener(event) }
 * }
 * ```
 *
 * Thread-safety: Implementations **must** be thread-safe. `subscribe`, `clear`, and `forEach` can
 * be invoked from different coroutines or threads. Implementations **must not** hold locks while
 * invoking user code passed to [forEach], to avoid deadlocks or long pauses.
 *
 * @param T the listener type (often a function type, e.g. `(Event) -> Unit`)
 */
@StreamInternalApi
public interface StreamSubscriptionManager<T> {
    /**
     * Subscription behavior options.
     *
     * @property retention Controls how the manager retains the listener reference.
     */
    public data class Options(val retention: Retention = Retention.AUTO_REMOVE) {
        /** Retention policy for a subscribed listener. */
        public enum class Retention {
            /**
             * The manager keeps only an ephemeral reference. If caller code drops all references to
             * the listener (and does not call `cancel()`), the listener is automatically removed
             * and stops receiving events.
             */
            AUTO_REMOVE,

            /**
             * The manager retains a strong reference to the listener until `cancel()` is called on
             * the returned subscription or [clear] is invoked.
             */
            KEEP_UNTIL_CANCELLED,
        }
    }

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

    /**
     * Removes **all** listeners and releases related resources.
     *
     * After a successful call:
     * - Existing [StreamSubscription] handles become no-ops.
     * - Subsequent [forEach] iterations will see an empty set until new listeners are added.
     *
     * @return `Result.success(Unit)` if cleared successfully; `Result.failure(Throwable)`
     *   otherwise.
     */
    public fun clear(): Result<Unit>

    /**
     * Executes [block] for every currently registered listener.
     *
     * Implementations should tolerate listeners being added or removed concurrently, but **must
     * not** hold locks while invoking [block].
     *
     * Any exceptions thrown by [block] should be accumulated (e.g., into an aggregate exception)
     * and returned as a failure rather than stopping iteration mid-way.
     *
     * @param block The action to execute for each listener.
     * @return `Result.success(Unit)` on normal completion; `Result.failure(Throwable)` if iteration
     *   fails.
     */
    public fun forEach(block: (T) -> Unit): Result<Unit>
}

/**
 * Creates a new [StreamSubscriptionManager] instance.
 *
 * @param T The listener type (often a function type, e.g. `(Event) -> Unit`).
 * @param logger The logger to use for logging.
 * @param maxStrongSubscriptions The maximum number of strong (non-weak) listeners.
 * @param maxWeakSubscriptions The maximum number of weak listeners.
 * @return A new [StreamSubscriptionManager] instance.
 */
@StreamInternalApi
public fun <T> StreamSubscriptionManager(
    logger: StreamLogger,
    maxStrongSubscriptions: Int = StreamSubscriptionManagerImpl.MAX_LISTENERS,
    maxWeakSubscriptions: Int = StreamSubscriptionManagerImpl.MAX_LISTENERS,
): StreamSubscriptionManager<T> =
    StreamSubscriptionManagerImpl(
        logger = logger,
        maxStrongSubscriptions = maxStrongSubscriptions,
        maxWeakSubscriptions = maxWeakSubscriptions,
    )
