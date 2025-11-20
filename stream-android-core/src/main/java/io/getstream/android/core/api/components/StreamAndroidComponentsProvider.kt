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
package io.getstream.android.core.api.components

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.telephony.TelephonyManager
import androidx.lifecycle.Lifecycle
import io.getstream.android.core.annotations.StreamInternalApi
import io.getstream.android.core.internal.components.StreamAndroidComponentsProviderImpl

/**
 * Facade over the Android system services required by the core SDK.
 *
 * Abstracting the access behind this interface allows Stream components to operate in tests,
 * host-apps with custom dependency wiring, or alternative runtime environments.
 *
 * ### Typical usage
 *
 * ```kotlin
 * val components = StreamAndroidComponentsProvider(context)
 * val connectivity = components.connectivityManager().getOrNull()
 * val lifecycle = components.lifecycle()
 * ```
 */
@StreamInternalApi
public interface StreamAndroidComponentsProvider {

    /**
     * Retrieves the [ConnectivityManager] system service.
     *
     * ### Example
     *
     * ```kotlin
     * val connectivity: ConnectivityManager =
     *     components.connectivityManager().getOrElse { throwable ->
     *         logger.e(throwable) { "Connectivity unavailable" }
     *         throw throwable
     *     }
     * ```
     *
     * @return [Result.success] with the manager, or [Result.failure] when the service is missing.
     */
    public fun connectivityManager(): Result<ConnectivityManager>

    /**
     * Retrieves the [WifiManager] system service.
     *
     * ### Example
     *
     * ```kotlin
     * val isWifiEnabled = components.wifiManager()
     *     .map { wifi -> wifi.isWifiEnabled }
     *     .getOrDefault(false)
     * ```
     *
     * @return [Result.success] with the manager, or [Result.failure] when the service is missing.
     */
    public fun wifiManager(): Result<WifiManager>

    /**
     * Retrieves the [TelephonyManager] system service.
     *
     * ### Example
     *
     * ```kotlin
     * val networkType = components.telephonyManager()
     *     .map { telephony -> telephony.dataNetworkType }
     *     .getOrElse { TelephonyManager.NETWORK_TYPE_UNKNOWN }
     * ```
     *
     * @return [Result.success] with the manager, or [Result.failure] when the service is missing.
     */
    public fun telephonyManager(): Result<TelephonyManager>

    /**
     * Retrieves the [Lifecycle] for the application.
     *
     * ### Example
     *
     * ```kotlin
     * components.lifecycle().addObserver(lifecycleObserver)
     * ```
     *
     * @return The process-level [Lifecycle].
     */
    public fun lifecycle(): Lifecycle
}

/**
 * Creates a new [StreamAndroidComponentsProvider] instance.
 *
 * @param context The application context.
 * @return A new [StreamAndroidComponentsProvider] instance.
 */
@StreamInternalApi
public fun StreamAndroidComponentsProvider(context: Context): StreamAndroidComponentsProvider =
    StreamAndroidComponentsProviderImpl(context)
