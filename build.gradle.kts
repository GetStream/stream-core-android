import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.vanniktech.maven.publish.MavenPublishBaseExtension
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

subprojects {
    plugins.withId("com.vanniktech.maven.publish") {
        extensions.configure<MavenPublishBaseExtension> {
            publishToMavenCentral(automaticRelease = true)

            pom {
                name.set("Stream Android Core")
                description.set("Stream Core official Android SDK")
                url.set("https://github.com/getstream/stream-core-android")

                licenses {
                    license {
                        name.set("Stream License")
                        url.set("https://github.com/GetStream/stream-core-android/blob/main/LICENSE")
                    }
                }

                developers {
                    developer {
                        id = "aleksandar-apostolov"
                        name = "Aleksandar Apostolov"
                        email = "aleksandar.apostolov@getstream.io"
                    }
                    developer {
                        id = "VelikovPetar"
                        name = "Petar Velikov"
                        email = "petar.velikov@getstream.io"
                    }
                    developer {
                        id = "andremion"
                        name = "André Mion"
                        email = "andre.rego@getstream.io"
                    }
                    developer {
                        id = "rahul-lohra"
                        name = "Rahul Kumar Lohra"
                        email = "rahul.lohra@getstream.io"
                    }
                    developer {
                        id = "gpunto"
                        name = "Gianmarco David"
                        email = "gianmarco.david@getstream.io"
                    }
                }

                scm {
                    connection.set("scm:git:github.com/getstream/stream-core-android.git")
                    developerConnection.set("scm:git:ssh://github.com/getstream/stream-core-android.git")
                    url.set("https://github.com/getstream/stream-core-android/tree/main")
                }
            }
        }
    }
}

tasks.register("printAllArtifacts") {
    group = "publishing"
    description = "Prints all artifacts that will be published"

    doLast {
        subprojects.forEach { subproject ->
            subproject.plugins.withId("com.vanniktech.maven.publish") {
                subproject.extensions.findByType(PublishingExtension::class.java)
                    ?.publications
                    ?.filterIsInstance<MavenPublication>()
                    ?.forEach { println("${it.groupId}:${it.artifactId}:${it.version}") }
            }
        }
    }
}
