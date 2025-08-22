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
package io.getstream.android.core.api.socket.monitor

import io.getstream.android.core.annotations.StreamCoreApi
import io.getstream.android.core.api.log.StreamLogger
import io.getstream.android.core.internal.socket.monitor.StreamHealthMonitorImpl
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.CoroutineScope

/**
 * Monitors the health and liveness of a system or connection by tracking periodic heartbeats and
 * thresholds for missed acknowledgments.
 *
 * Typical usage:
 * - Register a callback to be invoked on every heartbeat tick (e.g., for diagnostics).
 * - Register a callback to be invoked if no acknowledgment occurs within the allowed threshold.
 * - Call [acknowledgeHeartbeat] when receiving a valid "I'm alive" signal.
 * - Start and stop the monitor as needed.
 */
@StreamCoreApi
interface StreamHealthMonitor {
    /**
     * Registers a callback that is invoked at every heartbeat interval.
     *
     * This can be used for periodic checks, metrics collection, or emitting a signal to verify that
     * the system is responsive.
     *
     * @param callback A function to be called on each heartbeat tick.
     */
    fun onHeartbeat(callback: suspend () -> Unit)

    /**
     * Registers a callback that is invoked when the liveness threshold is exceeded.
     *
     * This typically means no acknowledgment was received within the expected time frame, and the
     * monitored system should be considered unhealthy, stalled, or disconnected.
     *
     * @param callback A function to be called when the liveness timeout occurs.
     */
    fun onUnhealthy(callback: suspend () -> Unit)

    /**
     * Acknowledges a heartbeat signal.
     *
     * This should be called whenever the monitored system successfully responds, indicating that it
     * is alive and healthy. Resets the liveness timer.
     */
    fun acknowledgeHeartbeat()

    /** Starts the health monitor, beginning the heartbeat and liveness checks. */
    fun start()

    /** Stops the health monitor, halting heartbeat and liveness checks. */
    fun stop()
}

@OptIn(ExperimentalTime::class)
@StreamCoreApi
fun StreamHealthMonitor(
    logger: StreamLogger,
    scope: CoroutineScope,
    interval: Long = StreamHealthMonitorImpl.INTERVAL,
    livenessThreshold: Long = StreamHealthMonitorImpl.ALIVE_THRESHOLD,
): StreamHealthMonitor = StreamHealthMonitorImpl(logger, scope, interval, livenessThreshold)
