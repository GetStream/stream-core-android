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
import io.getstream.android.core.annotations.StreamInternalApi
import io.getstream.android.core.internal.components.StreamAndroidComponentsProviderImpl

/**
 * Provides access to Android system services.
 *
 * This interface abstracts away the details of accessing Android system services, allowing the SDK
 * to work with different versions of Android and different build environments.
 */
@StreamInternalApi
public interface StreamAndroidComponentsProvider {

    /**
     * Retrieves the [ConnectivityManager] system service.
     *
     * @return A [Result] containing the [ConnectivityManager] if successful, or an error if the
     *   service cannot be retrieved.
     */
    public fun connectivityManager(): Result<ConnectivityManager>

    /**
     * Retrieves the [WifiManager] system service.
     *
     * @return A [Result] containing the [WifiManager] if successful, or an error if the service
     *   cannot be retrieved.
     */
    public fun wifiManager(): Result<WifiManager>

    /**
     * Retrieves the [TelephonyManager] system service.
     *
     * @return A [Result] containing the [TelephonyManager] if successful, or an error if the
     *   service cannot be retrieved.
     */
    public fun telephonyManager(): Result<TelephonyManager>
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
