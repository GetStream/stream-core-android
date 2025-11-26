@file:OptIn(ExperimentalAbiValidation::class)

import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import io.getstream.core.Configuration
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.stream.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.arturbosch.detekt)
    alias(libs.plugins.sonarqube)
    alias(libs.plugins.kover)
    alias(libs.plugins.maven.publish)
}

kotlin {
    explicitApi()
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        freeCompilerArgs.addAll(
            "-opt-in=io.getstream.android.core.annotations.StreamInternalApi",
            "-XXLanguage:+PropertyParamAnnotationDefaultTargetMode",
        )
    }
}

android {
    namespace = "io.getstream.android.core"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
        lintConfig = rootProject.file("lint.xml")
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

    // Android
    implementation(libs.androidx.annotation.jvm)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.process)

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

mavenPublishing {
    coordinates(
        groupId = Configuration.artifactGroup,
        artifactId = "stream-android-core",
        version = rootProject.version.toString(),
    )
    configure(
        AndroidSingleVariantLibrary(
            variant = "release",
            sourcesJar = true,
            publishJavadocJar = true,
        ),
    )
}
