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

package io.getstream.android.core.internal.processing

import io.getstream.android.core.api.log.StreamLogger
import io.getstream.android.core.api.processing.StreamAggregatedEvent
import io.getstream.android.core.api.processing.StreamEventAggregator
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Internal implementation of [StreamEventAggregator].
 *
 * Uses two decoupled coroutines:
 * - **Collector:** Drains [inbox], groups by type, packages into [dispatchQueue].
 * - **Dispatcher:** Takes from [dispatchQueue], calls the registered handler.
 *
 * Collection stops when [aggregationThreshold] items accumulate or [maxWindowMs] elapses. The
 * dispatch queue is bounded by [dispatchQueueCapacity] — if full, the collector drops the delivery
 * and logs a warning.
 */
internal class StreamEventAggregatorImpl<T>(
    private val scope: CoroutineScope,
    private val typeExtractor: (String) -> String?,
    private val deserializer: (String) -> Result<T>,
    private val aggregationThreshold: Int = 50,
    private val maxWindowMs: Long = 500L,
    private val dispatchQueueCapacity: Int = 16,
    inboxCapacity: Int = Channel.UNLIMITED,
    internal var logger: StreamLogger? = null,
) : StreamEventAggregator<T> {

    private val inbox = Channel<String>(inboxCapacity)
    private val dispatchQueue = Channel<DispatchItem<T>>(dispatchQueueCapacity)
    private val started = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private var collectorJob: Job? = null
    private var dispatcherJob: Job? = null

    private val eventHandler = AtomicReference<suspend (Any) -> Unit> { /* no-op until set */ }

    override fun onEvent(handler: suspend (Any) -> Unit) {
        eventHandler.set(handler)
    }

    override fun start(): Result<Unit> = runCatching {
        check(!closed.get()) { "StreamEventAggregator has already been stopped" }
        if (!started.compareAndSet(false, true)) return Result.success(Unit)
        collectorJob = scope.launch { runCollector() }
        dispatcherJob = scope.launch { runDispatcher() }
    }

    override fun offer(raw: String): Boolean {
        if (closed.get()) return false
        if (!started.get()) {
            // Auto-start on first offer
            start()
        }
        return inbox.trySend(raw).isSuccess
    }

    override fun stop(): Result<Unit> = runCatching {
        if (!closed.compareAndSet(false, true)) return Result.success(Unit)
        started.set(false)
        collectorJob?.cancel()
        dispatcherJob?.cancel()
        collectorJob = null
        dispatcherJob = null
        inbox.close()
        dispatchQueue.close()
    }

    private suspend fun runCollector() {
        try {
            while (scope.isActive) {
                // Wait for the first event — suspends until something arrives
                val first = inbox.receive()
                val buffer = mutableListOf(first)

                // Collect more until threshold or maxWindow
                collectWindow(buffer)

                // Package and send to dispatch queue
                val item = packageForDispatch(buffer)
                if (item != null) {
                    val sent = dispatchQueue.trySend(item)
                    if (sent.isFailure) {
                        logger?.w {
                            "[collector] Dispatch queue full (capacity=$dispatchQueueCapacity). " +
                                "Dropping ${buffer.size} events. Dispatcher may be too slow."
                        }
                    }
                }
            }
        } catch (_: ClosedReceiveChannelException) {
            // Inbox closed — clean shutdown
        } catch (_: CancellationException) {
            // Scope cancelled
        }
    }

    /**
     * Collects events from the inbox until [aggregationThreshold] is reached or [maxWindowMs]
     * elapses. Uses [kotlinx.coroutines.withTimeoutOrNull] so virtual-time test dispatchers can
     * advance the clock correctly.
     */
    private suspend fun collectWindow(buffer: MutableList<String>) {
        kotlinx.coroutines.withTimeoutOrNull(maxWindowMs) {
            while (buffer.size < aggregationThreshold) {
                buffer += inbox.receive()
            }
        }
    }

    /** Deserializes raw messages and decides: individual dispatch or aggregated. */
    private fun packageForDispatch(rawMessages: List<String>): DispatchItem<T>? {
        if (rawMessages.isEmpty()) return null

        if (rawMessages.size < aggregationThreshold) {
            // Low traffic — deserialize and dispatch individually
            val events = mutableListOf<DeserializedEvent<T>>()
            for (raw in rawMessages) {
                val parsed = safeDeserialize(raw) ?: continue
                events += DeserializedEvent(raw, parsed)
            }
            return if (events.isEmpty()) null else DispatchItem.Individual(events)
        }

        // Spike — group by type
        val grouped = LinkedHashMap<String, MutableList<T>>()
        for (raw in rawMessages) {
            val type = safeExtractType(raw)
            val event = safeDeserialize(raw) ?: continue
            grouped.getOrPut(type) { mutableListOf() }.add(event)
        }
        return if (grouped.isEmpty()) null
        else DispatchItem.Aggregated(StreamAggregatedEvent(grouped))
    }

    /** Calls [deserializer], catching both Result.failure and thrown exceptions. */
    private fun safeDeserialize(raw: String): T? =
        try {
            deserializer(raw)
                .onFailure { e ->
                    logger?.e(e) { "[collector] Failed to deserialize event. ${e.message}" }
                }
                .getOrNull()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger?.e(e) { "[collector] Deserializer threw. ${e.message}" }
            null
        }

    /** Calls [typeExtractor], catching thrown exceptions. Returns empty string on failure. */
    private fun safeExtractType(raw: String): String =
        try {
            typeExtractor(raw) ?: ""
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger?.e(e) { "[collector] Type extractor threw. ${e.message}" }
            ""
        }

    private suspend fun runDispatcher() {
        try {
            for (item in dispatchQueue) {
                when (item) {
                    is DispatchItem.Individual -> {
                        for (event in item.events) {
                            try {
                                eventHandler.get().invoke(event.parsed as Any)
                            } catch (ce: CancellationException) {
                                throw ce
                            } catch (e: Throwable) {
                                logger?.e(e) {
                                    "[dispatcher] Handler threw on individual event. ${e.message}"
                                }
                            }
                        }
                    }

                    is DispatchItem.Aggregated -> {
                        try {
                            eventHandler.get().invoke(item.aggregated)
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (e: Throwable) {
                            logger?.e(e) {
                                "[dispatcher] Handler threw on aggregated event. ${e.message}"
                            }
                        }
                    }
                }
            }
        } catch (_: ClosedReceiveChannelException) {
            // Dispatch queue closed
        } catch (_: CancellationException) {
            // Scope cancelled
        }
    }

    /** A single deserialized event with its raw source preserved for logging. */
    private data class DeserializedEvent<T>(val raw: String, val parsed: T)

    /** Work item in the dispatch queue. */
    private sealed class DispatchItem<T> {
        data class Individual<T>(val events: List<DeserializedEvent<T>>) : DispatchItem<T>()

        data class Aggregated<T>(val aggregated: StreamAggregatedEvent<T>) : DispatchItem<T>()
    }
}
