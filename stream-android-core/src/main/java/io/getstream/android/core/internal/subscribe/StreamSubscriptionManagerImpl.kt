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
package io.getstream.android.core.internal.subscribe

import io.getstream.android.core.api.log.StreamLogger
import io.getstream.android.core.api.model.exceptions.StreamAggregateException
import io.getstream.android.core.api.subscribe.StreamSubscription
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager.Options
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager.Options.Retention
import io.getstream.android.core.api.utils.streamComputeIfAbsent
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

internal class StreamSubscriptionManagerImpl<T>(
    private val logger: StreamLogger,
    private val strongSubscribers: ConcurrentHashMap<T, StreamSubscription> = ConcurrentHashMap(),
    private val weakSubscribers: MutableMap<T, StreamSubscription> =
        Collections.synchronizedMap(WeakHashMap()),
    private val maxStrongSubscriptions: Int = MAX_LISTENERS,
    private val maxWeakSubscriptions: Int = MAX_LISTENERS,
) : StreamSubscriptionManager<T> {
    companion object {
        internal const val MAX_LISTENERS = 250
    }

    override fun subscribe(listener: T, options: Options): Result<StreamSubscription> =
        when (options.retention) {
            Retention.AUTO_REMOVE -> subscribeWeak(listener)
            Retention.KEEP_UNTIL_CANCELLED -> subscribeStrong(listener)
        }

    override fun clear(): Result<Unit> = runCatching {
        logger.d { "Clearing all listeners" }
        strongSubscribers.clear()
        synchronized(weakSubscribers) { weakSubscribers.clear() }
    }

    override fun forEach(block: (T) -> Unit): Result<Unit> = runCatching {
        logger.v {
            "Notifying subscribers S-(${strongSubscribers.size}: ${strongSubscribers.keys}), W-(${weakSubscribers.size}: ${weakSubscribers.keys})"
        }
        val errors = mutableListOf<Throwable>()
        // Strong subscribers: lock-free iteration
        strongSubscribers.keys.forEach { listener ->
            runCatching { block(listener) }.onFailure(errors::add)
        }
        // Weak subscribers: snapshot keys under lock, then invoke outside lock
        val weakKeys: List<T> = synchronized(weakSubscribers) { weakSubscribers.keys.toList() }
        weakKeys.forEach { listener -> runCatching { block(listener) }.onFailure(errors::add) }

        if (errors.isNotEmpty()) {
            logger.v { "Listener errors: $errors" }
            throw StreamAggregateException("Failed to notify all listeners", errors)
        }
    }

    private fun subscribeStrong(listener: T): Result<StreamSubscription> = runCatching {
        logger.v { "Subscribing strong listener" }
        strongSubscribers[listener]?.let {
            logger.v { "Strong listener already exists" }
            return@runCatching it
        }

        if (strongSubscribers.size >= maxStrongSubscriptions) {
            logger.e { "Max strong listeners reached" }
            throw IllegalStateException(
                "Max strong listeners ($maxStrongSubscriptions) reached; unsubscribe some listeners."
            )
        }

        strongSubscribers.streamComputeIfAbsent(listener) {
            object : StreamSubscription {
                override fun cancel() {
                    strongSubscribers.remove(listener, this)
                }
            }
        }
    }

    private fun subscribeWeak(listener: T): Result<StreamSubscription> = runCatching {
        logger.v { "Subscribing weak listener" }
        weakSubscribers[listener]?.let {
            logger.v { "Weak listener already exists" }
            return@runCatching it
        }
        synchronized(weakSubscribers) {
            logger.v { "Weak listener does not exist, creating new subscription" }
            val subscription = weakSubscribers[listener]
            if (subscription != null) {
                logger.v { "Weak listener already exists after synchronized check" }
                return@runCatching subscription
            }
            if (weakSubscribers.size >= maxWeakSubscriptions) {
                logger.e { "Max weak listeners reached" }
                throw IllegalStateException(
                    "Max weak listeners ($maxWeakSubscriptions) reached; unsubscribe some listeners."
                )
            }
            val keyRef = WeakReference(listener)
            val handle =
                object : StreamSubscription {
                    override fun cancel() {
                        val key = keyRef.get() ?: return
                        synchronized(weakSubscribers) { weakSubscribers.remove(key, this) }
                    }
                }
            weakSubscribers[listener] = handle
            handle
        }
    }
}
