package io.getstream.android.core.api.model.retry

import io.getstream.android.core.annotations.StreamInternalApi
import io.getstream.android.core.api.model.retry.StreamRetryPolicy

/**
 * Snapshot describing the state of the current retry loop iteration.
 *
 * Instances are delivered to the `block` passed into
 * [StreamRetryProcessor.retry][io.getstream.android.core.api.processing.StreamRetryProcessor.retry]
 * before each attempt so callers can log, alter metrics, or branch based on the retry count.
 *
 * @property attempt 1-based attempt index. `1` represents the first invocation after the initial
 *   failure.
 * @property currentDelay Delay (in milliseconds) that was applied before this attempt. Equals the
 *   policy's `initialDelayMillis` for the first retry.
 * @property previousAttemptError The error thrown by the previous attempt, if any. `null` for the
 *   first attempt.
 * @property policy The retry policy governing this run; useful if callers need to inspect limits
 *   (max retries, back-off window, etc.).
 */
@StreamInternalApi
public data class StreamRetryAttemptInfo(
    val attempt: Int,
    val currentDelay: Long,
    val previousAttemptError: Throwable? = null,
    val policy: StreamRetryPolicy,
)
