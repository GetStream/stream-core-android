plugins {
    id("java-library")
    alias(libs.plugins.jetbrains.kotlin.jvm)
}

rootProject.extra.apply {
    set("PUBLISH_GROUP_ID", io.getstream.core.Configuration.artifactGroup)
    set("PUBLISH_ARTIFACT_ID", "stream-android-core-annotations")
    set("PUBLISH_VERSION", rootProject.extra.get("rootVersionName"))
}

apply(from = "${rootDir}/scripts/publish-module.gradle")

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}
