import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import io.getstream.core.Configuration

apply(from = "${rootDir}/gradle/scripts/sonar.gradle")
// Top-level build file where you can add configuration options common to all sub-projects/modules.
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
    alias(libs.plugins.sonarqube) apply true
    alias(libs.plugins.kover) apply true
}

streamProject {
    repositoryName = "stream-core-android"

    spotless {
        useKtfmt = true
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
