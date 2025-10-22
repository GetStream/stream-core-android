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

import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.SignalStrength
import android.telephony.TelephonyManager
import io.getstream.android.core.api.model.connection.network.StreamNetworkInfo.Signal
import io.getstream.android.core.api.model.connection.network.StreamNetworkInfo.Transport

internal class StreamNetworkSignalProcessing {

    fun bestEffortSignal(
        wifiManager: WifiManager,
        telephonyManager: TelephonyManager,
        capabilities: NetworkCapabilities?,
        transports: Set<Transport>,
    ): Signal? {
        genericSignal(capabilities)?.let {
            return it
        }

        return when {
            Transport.WIFI in transports -> wifiSignal(wifiManager)
            Transport.CELLULAR in transports -> cellularSignal(telephonyManager)
            else -> null
        }
    }

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

    fun cellularSignal(telephonyManager: TelephonyManager): Signal.Cellular? {
        val strength = telephonySignalStrength(telephonyManager) ?: return null
        val level = signalLevel(strength)

        nrReport(strength)?.let { nr ->
            return Signal.Cellular(
                rat = "NR",
                level0to4 = level,
                rsrpDbm = nr.ssRsrpValue(),
                rsrqDb = nr.ssRsrqValue(),
                sinrDb = nr.ssSinrValue(),
            )
        }

        lteReport(strength)?.let { lte ->
            return Signal.Cellular(
                rat = "LTE",
                level0to4 = level,
                rsrpDbm = lte.rsrpValue(),
                rsrqDb = lte.rsrqValue(),
                sinrDb = lte.rssnrValue(),
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

    private fun genericSignal(capabilities: NetworkCapabilities?): Signal.Generic? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return null
        }
        val strength = capabilities?.signalStrength?.takeIf { it != Int.MIN_VALUE } ?: return null
        return Signal.Generic(strength)
    }

    private fun sanitizeSsid(info: WifiInfo): String? {
        val raw = info.ssid?.trim('"') ?: return null
        val isPlatformUnknown =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                raw == WifiManager.UNKNOWN_SSID
            } else {
                false
            }
        val isLegacyUnknown = raw == "<unknown ssid>"
        return raw.takeUnless { isPlatformUnknown || isLegacyUnknown }
    }

    private fun wifiSignalLevel(rssi: Int, numLevels: Int = 5): Int? =
        runCatching { WifiManager.calculateSignalLevel(rssi, numLevels) }.getOrNull()

    private fun telephonySignalStrength(manager: TelephonyManager): SignalStrength? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) manager.signalStrength else null

    private fun signalLevel(strength: SignalStrength?): Int? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) strength?.level else null

    private fun nrReport(strength: SignalStrength?): CellSignalStrengthNr? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            strength?.cellSignalStrengths?.filterIsInstance<CellSignalStrengthNr>()?.firstOrNull()
        } else {
            null
        }

    private fun lteReport(strength: SignalStrength?): CellSignalStrengthLte? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            strength?.cellSignalStrengths?.filterIsInstance<CellSignalStrengthLte>()?.firstOrNull()
        } else {
            null
        }

    private fun CellSignalStrengthNr.ssRsrpValue(): Int? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ssRsrp else null

    private fun CellSignalStrengthNr.ssRsrqValue(): Int? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ssRsrq else null

    private fun CellSignalStrengthNr.ssSinrValue(): Int? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ssSinr else null

    private fun CellSignalStrengthLte.rsrpValue(): Int? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) rsrp else null

    private fun CellSignalStrengthLte.rsrqValue(): Int? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) rsrq else null

    private fun CellSignalStrengthLte.rssnrValue(): Int? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) rssnr else null
}
