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

package io.getstream.android.core.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue
import io.getstream.android.core.lint.detectors.ExposeAsStateFlowDetector
import io.getstream.android.core.lint.detectors.KeepInstanceDetector
import io.getstream.android.core.lint.detectors.MustBeInternalDetector
import io.getstream.android.core.lint.detectors.StreamApiExplicitMarkerDetector
import io.getstream.android.core.lint.detectors.SuspendRunCatchingDetector

/** The stream lint rules registry. */
class StreamIssueRegistry : IssueRegistry() {
    override val api: Int = CURRENT_API
    override val issues: List<Issue> =
        listOf(
            MustBeInternalDetector.ISSUE,
            KeepInstanceDetector.ISSUE,
            SuspendRunCatchingDetector.ISSUE,
            ExposeAsStateFlowDetector.ISSUE,
            StreamApiExplicitMarkerDetector.ISSUE,
        )

    override val vendor =
        Vendor(
            vendorName = "GetStream",
            feedbackUrl = "https://getstream.io",
            contact = "android@getstream.io",
        )
}
