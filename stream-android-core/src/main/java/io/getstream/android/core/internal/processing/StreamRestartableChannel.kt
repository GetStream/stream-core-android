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

@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.getstream.android.core.internal.processing

import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.selects.SelectClause1
import kotlinx.coroutines.selects.SelectClause2

/**
 * A Channel<T> wrapper that can be "restarted".
 * - Delegates all Channel operations to an internal Channel<T>.
 * - When [start] is called after the channel was closed-for-send, it swaps in a fresh channel.
 * - Adds `*Safe` helpers that wrap send/trySend/close in Result for ergonomic error handling.
 */
@OptIn(DelicateCoroutinesApi::class)
internal class StreamRestartableChannel<T>(private val capacity: Int = Channel.BUFFERED) :
    Channel<T> {

    private val ref = AtomicReference<Channel<T>>(newChannel())
    private val delegate: Channel<T>
        get() = ref.get()

    override suspend fun send(element: T) = delegate.send(element)

    override fun trySend(element: T): ChannelResult<Unit> = delegate.trySend(element)

    override suspend fun receive(): T = delegate.receive()

    override fun tryReceive(): ChannelResult<T> = delegate.tryReceive()

    override suspend fun receiveCatching(): ChannelResult<T> = delegate.receiveCatching()

    override val onSend: SelectClause2<T, SendChannel<T>>
        get() = delegate.onSend

    override val onReceive: SelectClause1<T>
        get() = delegate.onReceive

    override val onReceiveCatching: SelectClause1<ChannelResult<T>>
        get() = delegate.onReceiveCatching

    @Deprecated(
        "Since 1.2.0, binary compatibility with versions <= 1.1.x",
        level = DeprecationLevel.HIDDEN,
    )
    override fun cancel(cause: Throwable?): Boolean {
        delegate.cancel(CancellationException("Channel cancelled", cause))
        return true
    }

    override val isClosedForSend: Boolean
        get() = delegate.isClosedForSend

    override val isClosedForReceive: Boolean
        get() = delegate.isClosedForReceive

    override val isEmpty: Boolean
        get() = delegate.isEmpty

    override fun iterator(): ChannelIterator<T> = delegate.iterator()

    override fun invokeOnClose(handler: (cause: Throwable?) -> Unit) =
        delegate.invokeOnClose(handler)

    override fun close(cause: Throwable?): Boolean = delegate.close(cause)

    override fun cancel(cause: CancellationException?) = delegate.cancel(cause)

    /**
     * Reopens the channel if it's closed-for-send. No-op if already open. Any pending
     * receivers/senders on the old channel get completed/failed according to normal close
     * semantics.
     */
    fun start() {
        val old = ref.get()
        if (!old.isClosedForSend) {
            return
        }
        val fresh = newChannel()
        ref.set(fresh)
        old.close()
    }

    /** Convenience predicate that checks both ends. */
    fun isClosed(): Boolean = isClosedForSend || isClosedForReceive

    private fun newChannel(): Channel<T> = Channel(capacity)
}
