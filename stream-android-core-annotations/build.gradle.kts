plugins {
    alias(libs.plugins.stream.java.library)
    alias(libs.plugins.jetbrains.kotlin.jvm)
}

rootProject.extra.apply {
    set("PUBLISH_GROUP_ID", io.getstream.core.Configuration.artifactGroup)
    set("PUBLISH_ARTIFACT_ID", "stream-android-core-annotations")
    set("PUBLISH_VERSION", rootProject.extra.get("rootVersionName"))
}

apply(from = "${rootDir}/scripts/publish-module.gradle")

java {
    withSourcesJar()
}
