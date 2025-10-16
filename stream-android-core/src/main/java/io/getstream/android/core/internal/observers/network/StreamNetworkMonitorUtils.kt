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

internal fun NetworkCapabilities.safeHasCapability(capability: Int): Boolean? =
    runCatching { hasCapability(capability) }.getOrNull()

internal fun NetworkCapabilities.safeHasTransport(transport: Int): Boolean? =
    runCatching { hasTransport(transport) }.getOrNull()

internal fun sanitizeSsid(info: WifiInfo): String? {
    val raw = info.ssid?.trim('"') ?: return null
    val isPlatformUnknown =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) raw == WifiManager.UNKNOWN_SSID
        else false
    val isLegacyUnknown = raw == "<unknown ssid>"
    return raw.takeUnless { isPlatformUnknown || isLegacyUnknown }
}

internal fun wifiSignalLevel(rssi: Int, numLevels: Int = 5): Int? =
    runCatching { WifiManager.calculateSignalLevel(rssi, numLevels) }.getOrNull()

internal fun telephonySignalStrength(manager: TelephonyManager): SignalStrength? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) manager.signalStrength else null

internal fun signalLevel(strength: SignalStrength?): Int? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) strength?.level else null

internal fun nrStrength(strength: SignalStrength?): CellSignalStrengthNr? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        strength?.cellSignalStrengths?.filterIsInstance<CellSignalStrengthNr>()?.firstOrNull()
    } else {
        null
    }

internal fun lteStrength(strength: SignalStrength?): CellSignalStrengthLte? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        strength?.cellSignalStrengths?.filterIsInstance<CellSignalStrengthLte>()?.firstOrNull()
    } else {
        null
    }
