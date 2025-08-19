/*
 * Copyright (c) 2014-2025 Stream.io Inc. All rights reserved.
 *
 * Licensed under the Stream License;
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://github.com/GetStream/stream-android-base/blob/main/LICENSE
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.getstream.android.core.internal.subscribe

import io.getstream.android.core.api.model.exceptions.StreamAggregateException
import io.getstream.android.core.api.subscribe.StreamSubscription
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager.SubscribeOptions
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager.SubscribeOptions.SubscriptionRetention
import io.getstream.android.core.api.utils.streamComputeIfAbsent
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

internal class StreamSubscriptionManagerImpl<T>(
    private val strongSubscribers: ConcurrentHashMap<T, StreamSubscription> = ConcurrentHashMap(),
    private val weakSubscribers: MutableMap<T, StreamSubscription> =
        Collections.synchronizedMap(WeakHashMap()),
    private val maxStrongSubscriptions: Int = MAX_LISTENERS,
    private val maxWeakSubscriptions: Int = MAX_LISTENERS,
) : StreamSubscriptionManager<T> {

    companion object {
        internal const val MAX_LISTENERS = 250
    }

    override fun subscribe(listener: T, options: SubscribeOptions): Result<StreamSubscription> =
        when (options.retention) {
            SubscriptionRetention.AUTO_REMOVE -> subscribeWeak(listener)
            SubscriptionRetention.KEEP_UNTIL_CANCELLED -> subscribeStrong(listener)
        }

    override fun clear(): Result<Unit> = runCatching {
        strongSubscribers.clear()
        synchronized(weakSubscribers) { weakSubscribers.clear() }
    }

    override fun forEach(block: (T) -> Unit): Result<Unit> = runCatching {
        val errors = mutableListOf<Throwable>()

        // Strong subscribers: lock-free iteration
        strongSubscribers.keys.forEach { listener ->
            runCatching { block(listener) }.onFailure(errors::add)
        }

        // Weak subscribers: snapshot keys under lock, then invoke outside lock
        val weakKeys: List<T> = synchronized(weakSubscribers) { weakSubscribers.keys.toList() }
        weakKeys.forEach { listener -> runCatching { block(listener) }.onFailure(errors::add) }

        if (errors.isNotEmpty()) {
            throw StreamAggregateException("", errors)
        }
    }

    private fun subscribeStrong(listener: T): Result<StreamSubscription> = runCatching {
        strongSubscribers[listener]?.let {
            return@runCatching it
        }

        if (strongSubscribers.size >= maxStrongSubscriptions) {
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
        weakSubscribers[listener]?.let {
            return@runCatching it
        }
        synchronized(weakSubscribers) {
            val subscription = weakSubscribers[listener]
            if (subscription != null) {
                return@runCatching subscription
            }
            if (weakSubscribers.size >= maxWeakSubscriptions) {
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
