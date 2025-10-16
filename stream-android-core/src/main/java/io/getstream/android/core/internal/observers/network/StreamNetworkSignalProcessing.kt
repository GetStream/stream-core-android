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
package io.getstream.android.core.internal.observers.network

import android.annotation.SuppressLint
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager
import io.getstream.android.core.api.model.connection.network.StreamNetworkInfo.Signal
import io.getstream.android.core.api.model.connection.network.StreamNetworkInfo.Transport

internal class StreamNetworkSignalProcessing {

    @SuppressLint("MissingPermission")
    fun bestEffortSignal(
        wifiManager: WifiManager,
        telephonyManager: TelephonyManager,
        capabilities: NetworkCapabilities?,
        transports: Set<Transport>,
    ): Signal? {
        val genericValue =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                capabilities?.signalStrength?.takeIf { it != Int.MIN_VALUE }
            } else {
                null
            }
        if (genericValue != null) return Signal.Generic(genericValue)

        return when {
            Transport.WIFI in transports -> wifiSignal(wifiManager)
            Transport.CELLULAR in transports -> cellularSignal(telephonyManager)
            else -> null
        }
    }

    @SuppressLint("MissingPermission")
    fun wifiSignal(wifiManager: WifiManager): Signal.Wifi? {
        val info = wifiManager.connectionInfo ?: return null
        val rssi = info.rssi
        return Signal.Wifi(
            rssiDbm = rssi,
            level0to4 = wifiSignalLevel(rssi),
            ssid = sanitizeSsid(info),
            bssid = info.bssid,
            frequencyMhz = info.frequency.takeIf { it > 0 },
        )
    }

    @SuppressLint("MissingPermission")
    fun cellularSignal(telephonyManager: TelephonyManager): Signal.Cellular? {
        val strength = telephonySignalStrength(telephonyManager) ?: return null
        val level = signalLevel(strength)

        val nr = nrStrength(strength)
        if (nr != null) {
            val rsrp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) nr.ssRsrp else null
            val rsrq = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) nr.ssRsrq else null
            val sinr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) nr.ssSinr else null
            return Signal.Cellular(
                rat = "NR",
                level0to4 = level,
                rsrpDbm = rsrp,
                rsrqDb = rsrq,
                sinrDb = sinr,
            )
        }

        val lte = lteStrength(strength)
        if (lte != null) {
            val rsrp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) lte.rsrp else null
            val rsrq = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) lte.rsrq else null
            val sinr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) lte.rssnr else null
            return Signal.Cellular(
                rat = "LTE",
                level0to4 = level,
                rsrpDbm = rsrp,
                rsrqDb = rsrq,
                sinrDb = sinr,
            )
        }

        return Signal.Cellular(
            rat = null,
            level0to4 = level,
            rsrpDbm = null,
            rsrqDb = null,
            sinrDb = null,
        )
    }
}
