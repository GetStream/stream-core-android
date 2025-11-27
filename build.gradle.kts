import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import io.getstream.core.Configuration

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
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.arturbosch.detekt) apply true
}

streamProject {
    repositoryName = "stream-core-android"

    spotless {
        useKtfmt = true
    }

    coverage {
        includedModules = setOf("stream-android-core")
        sonarCoverageExclusions = setOf(
            "**/lint/**",
        )
    }
}

detekt {
    autoCorrect = true
    toolVersion = "1.23.8"
    config.setFrom(file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
}

private val isSnapshot = System.getenv("SNAPSHOT")?.toBoolean() == true
version = if (isSnapshot) Configuration.snapshotVersionName else Configuration.versionName

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
