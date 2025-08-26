import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.arturbosch.detekt)
    alias(libs.plugins.sonarqube)
    alias(libs.plugins.kover)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        freeCompilerArgs.addAll(
            "-opt-in=io.getstream.android.core.annotations.StreamCoreApi", "-XXLanguage:+PropertyParamAnnotationDefaultTargetMode"
        )
    }
}

android {
    namespace = "io.getstream.android.core"
    compileSdk = 36

    defaultConfig {
        minSdk = 21

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
}

dependencies {
    // Linter
    lintChecks(project(":stream-android-core-lint"))
    lintPublish(project(":stream-android-core-lint")) {
        isTransitive = false
    }
    implementation(project(":stream-android-core-annotations"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

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

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}