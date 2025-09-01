@file:OptIn(ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import io.getstream.core.Configuration
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.arturbosch.detekt)
    alias(libs.plugins.sonarqube)
    alias(libs.plugins.kover)
}

rootProject.extra.apply {
    set("PUBLISH_GROUP_ID", Configuration.artifactGroup)
    set("PUBLISH_ARTIFACT_ID", "stream-android-core")
    set("PUBLISH_VERSION", rootProject.extra.get("rootVersionName"))
}

apply(from = "${rootDir}/scripts/publish-module.gradle")

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        freeCompilerArgs.addAll(
            "-Xexplicit-api=strict",
            "-opt-in=io.getstream.android.core.annotations.StreamInternalApi",
            "-opt-in=io.getstream.android.core.annotations.StreamPublishedApi",
            "-XXLanguage:+PropertyParamAnnotationDefaultTargetMode"
        )
    }
}

android {
    namespace = "io.getstream.android.core"
    compileSdk = Configuration.compileSdk

    defaultConfig {
        minSdk = Configuration.minSdk

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
        lintConfig = rootProject.file("lint.xml")
    }

    publishing {
        singleVariant("release") { }
    }
}

dependencies {
    // Linter
    lintChecks(project(":stream-android-core-lint"))
    lintPublish(project(":stream-android-core-lint")) {
        isTransitive = false
    }
    implementation(project(":stream-android-core-annotations"))

    implementation(libs.kotlinx.coroutines)

    detektPlugins(libs.detekt.formatting)

    // Network
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)
    implementation(libs.moshi.adapters)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit)
    implementation(libs.retrofit.moshi)
    implementation(libs.retrofit.scalars)
    ksp(libs.moshi.codegen)

    // Robolectric for Android-ish tests on the JVM
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.core)
    testImplementation(kotlin("test"))
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver)
}