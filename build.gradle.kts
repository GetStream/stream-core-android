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

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
    alias(libs.plugins.stream.project)
    alias(libs.plugins.stream.android.library) apply false
    alias(libs.plugins.stream.android.application) apply false
    alias(libs.plugins.stream.java.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.jetbrains.kotlin.jvm) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.arturbosch.detekt) apply true
}

streamProject {
    repositoryName = "stream-core-android"

    spotless {
        useKtfmt = true
    }

    coverage {
        includedModules = setOf("stream-android-core")
    }

    publishing {
        description = "Stream Core official Android SDK"
    }
}

detekt {
    autoCorrect = true
    toolVersion = "1.23.8"
    config.setFrom(file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
}

subprojects {
    // Configure Android projects with common SDK versions as soon as either plugin is applied
    pluginManager.withPlugin("com.android.library") {
        extensions.configure<LibraryExtension> {
            defaultConfig {
                compileSdk = libs.versions.compileSdk.get().toInt()
                minSdk = libs.versions.minSdk.get().toInt()
                lint.targetSdk = libs.versions.targetSdk.get().toInt()
                testOptions.targetSdk = libs.versions.targetSdk.get().toInt()
            }
        }
    }
    pluginManager.withPlugin("com.android.application") {
        extensions.configure<ApplicationExtension> {
            defaultConfig {
                compileSdk = libs.versions.compileSdk.get().toInt()
                minSdk = libs.versions.minSdk.get().toInt()
                targetSdk = libs.versions.targetSdk.get().toInt()
            }
        }
    }
}
