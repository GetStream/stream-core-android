package io.getstream.android.core.api.utils

import io.getstream.android.core.annotations.StreamInternalApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Updates the value of the [MutableStateFlow] with the given [state].
 * Internally calls [MutableStateFlow.update] with a lambda that always returns the given [state].
 * More readable than `stateFlow.update { state }`.
 *
 * @param state The new value to set.
 */
@StreamInternalApi
public fun <T> MutableStateFlow<T>.update(state: T) {
    this.update { state }
}