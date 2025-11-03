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
package io.getstream.android.core.internal.components

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager
import io.getstream.android.core.api.components.StreamAndroidComponentsProvider

internal class StreamAndroidComponentsProviderImpl(context: Context) :
    StreamAndroidComponentsProvider {

    private val applicationContext = context.applicationContext

    override fun connectivityManager(): Result<ConnectivityManager> = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            applicationContext.getSystemService(ConnectivityManager::class.java)
        } else {
            applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        }
    }

    @SuppressLint("WifiManagerPotentialLeak")
    override fun wifiManager(): Result<WifiManager> = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            applicationContext.getSystemService(WifiManager::class.java)
        } else {
            applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        }
    }

    override fun telephonyManager(): Result<TelephonyManager> = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            applicationContext.getSystemService(TelephonyManager::class.java)
        } else {
            applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        }
    }
}
