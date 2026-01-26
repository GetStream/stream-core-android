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

package io.getstream.android.core.api.model.retry

import io.getstream.android.core.annotations.StreamInternalApi

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
