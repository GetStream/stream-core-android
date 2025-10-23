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
package io.getstream.android.core.api.utils

import io.getstream.android.core.annotations.StreamInternalApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Updates the value of the [MutableStateFlow] with the given [state]. Internally calls
 * [MutableStateFlow.update] with a lambda that always returns the given [state]. More readable than
 * `stateFlow.update { state }`.
 *
 * @param state The new value to set.
 */
@StreamInternalApi
public fun <T> MutableStateFlow<T>.update(state: T) {
    this.update { state }
}
