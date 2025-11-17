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

package io.getstream.android.core.api.observers.network

import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.telephony.TelephonyManager
import io.getstream.android.core.annotations.StreamInternalApi
import io.getstream.android.core.api.log.StreamLogger
import io.getstream.android.core.api.subscribe.StreamSubscription
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager
import io.getstream.android.core.internal.observers.network.StreamNetworkMonitorImpl
import io.getstream.android.core.internal.observers.network.StreamNetworkSignalProcessing
import io.getstream.android.core.internal.observers.network.StreamNetworkSnapshotBuilder
import kotlinx.coroutines.CoroutineScope

/**
 * Observes changes to the device's active network and provides snapshots of its capabilities.
 *
 * Implementations are expected to be life-cycle aware and safe to invoke from any thread.
 */
@StreamInternalApi
public interface StreamNetworkMonitor {

    /** Registers [listener] to receive network updates. */
    public fun subscribe(
        listener: StreamNetworkMonitorListener,
        options: StreamSubscriptionManager.Options = StreamSubscriptionManager.Options(),
    ): Result<StreamSubscription>

    /** Starts monitoring connectivity changes. Safe to call multiple times. */
    public fun start(): Result<Unit>

    /** Stops monitoring and releases platform callbacks. Safe to call multiple times. */
    public fun stop(): Result<Unit>
}

/**
 * Creates a [StreamNetworkMonitor] instance.
 *
 * @param logger The logger to use for logging.
 * @param scope The coroutine scope to use for running the monitor.
 * @param subscriptionManager The subscription manager to use for managing listeners.
 * @param componentsProvider Provides access to Android system services used for monitoring.
 */
@StreamInternalApi
public fun StreamNetworkMonitor(
    logger: StreamLogger,
    scope: CoroutineScope,
    subscriptionManager: StreamSubscriptionManager<StreamNetworkMonitorListener>,
    wifiManager: WifiManager,
    telephonyManager: TelephonyManager,
    connectivityManager: ConnectivityManager,
): StreamNetworkMonitor =
    StreamNetworkMonitorImpl(
        logger = logger,
        scope = scope,
        streamSubscriptionManager = subscriptionManager,
        snapshotBuilder =
            StreamNetworkSnapshotBuilder(
                signalProcessing = StreamNetworkSignalProcessing(),
                wifiManager = wifiManager,
                telephonyManager = telephonyManager,
            ),
        connectivityManager = connectivityManager,
    )
