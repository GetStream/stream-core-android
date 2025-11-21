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
import io.getstream.android.core.api.observers.StreamStartableComponent
import io.getstream.android.core.api.subscribe.StreamObservable
import io.getstream.android.core.api.subscribe.StreamSubscriptionManager
import io.getstream.android.core.internal.observers.network.StreamNetworkMonitorImpl
import io.getstream.android.core.internal.observers.network.StreamNetworkSignalProcessing
import io.getstream.android.core.internal.observers.network.StreamNetworkSnapshotBuilder
import kotlinx.coroutines.CoroutineScope

/**
 * Observes changes to the device's active network and provides snapshots of its capabilities.
 *
 * Implementations are expected to be lifecycle-aware and safe to invoke from any thread.
 *
 * ### Example
 *
 * ```kotlin
 * val subscription = monitor.subscribe(listener).getOrThrow()
 * monitor.start()
 *
 * // ... later ...
 * subscription.cancel()
 * monitor.stop()
 * ```
 */
@StreamInternalApi
public interface StreamNetworkMonitor :
    StreamStartableComponent, StreamObservable<StreamNetworkMonitorListener>

/**
 * Creates a [StreamNetworkMonitor] instance.
 *
 * @param logger The logger to use for logging.
 * @param scope The coroutine scope to use for running the monitor.
 * @param subscriptionManager The subscription manager to use for managing listeners.
 * @param wifiManager The Wi-Fi manager to use for accessing Wi-Fi information.
 * @param telephonyManager The telephony manager to use for accessing cellular information.
 * @param connectivityManager The connectivity manager to use for accessing network information.
 * @return A new [StreamNetworkMonitor] instance.
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
