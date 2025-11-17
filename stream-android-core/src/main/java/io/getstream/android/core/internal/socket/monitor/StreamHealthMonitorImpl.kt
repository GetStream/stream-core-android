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

package io.getstream.android.core.internal.socket.monitor

import io.getstream.android.core.api.log.StreamLogger
import io.getstream.android.core.api.socket.monitor.StreamHealthMonitor
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Monitors the health of a socket connection by periodically checking for liveness.
 *
 * @param logger Logger for logging health monitor events.
 * @param scope Coroutine scope for launching the health monitor job.
 * @param interval Time in milliseconds between health checks. Defaults to 25 seconds.
 * @param livenessThreshold Time in milliseconds after which the connection is considered dead if no
 *   acknowledgment is received. Defaults to 60 seconds.
 */
@OptIn(ExperimentalTime::class)
internal class StreamHealthMonitorImpl(
    private val logger: StreamLogger,
    private val scope: CoroutineScope,
    private val interval: Long = INTERVAL,
    private val livenessThreshold: Long = ALIVE_THRESHOLD,
    private val clock: Clock = Clock.System,
) : StreamHealthMonitor {
    companion object {
        const val INTERVAL = 25_000L
        const val ALIVE_THRESHOLD = 60_000L
    }

    private var monitorJob: Job? = null
    private var lastAck: Long = clock.now().toEpochMilliseconds()

    // callbacks default to no-op
    private var onIntervalCallback: suspend () -> Unit = {}
    private var onLivenessThresholdCallback: suspend () -> Unit = {}

    override fun onHeartbeat(callback: suspend () -> Unit) {
        onIntervalCallback = callback
    }

    override fun onUnhealthy(callback: suspend () -> Unit) {
        onLivenessThresholdCallback = callback
    }

    override fun acknowledgeHeartbeat() {
        lastAck = clock.now().toEpochMilliseconds()
    }

    /** Starts (or restarts) the periodic health-check loop */
    override fun start() = runCatching {
        logger.d { "[start] Staring health monitor" }
        if (monitorJob?.isActive == true) {
            logger.d { "Health monitor already running" }
            return@runCatching
        }
        monitorJob =
            scope.launch {
                while (isActive) {
                    delay(interval)

                    val now = clock.now().toEpochMilliseconds()
                    if (now - lastAck >= livenessThreshold) {
                        logger.d { "Liveness threshold reached" }
                        onLivenessThresholdCallback()
                    } else {
                        logger.d { "Running health check" }
                        onIntervalCallback()
                    }
                }
            }
    }

    /** Stops the health-check loop */
    override fun stop() = runCatching {
        logger.d { "[stop] Stopping heath monitor" }
        monitorJob?.cancel()
        Unit
    }
}
