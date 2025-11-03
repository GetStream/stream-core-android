import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm

plugins {
    id("java-library")
    alias(libs.plugins.jetbrains.kotlin.jvm)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.dokka)
}


java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}

dependencies {
    compileOnly(libs.lint.api)
    compileOnly(libs.lint.checks)

    testImplementation(kotlin("test"))
    testImplementation(libs.lint.tests)
}

// Register our Lint IssueRegistry in the jar manifest
tasks.jar {
    manifest {
        attributes["Lint-Registry-v2"] = "io.getstream.android.core.lint.StreamIssueRegistry"
    }
}

mavenPublishing {
    coordinates(
        groupId = io.getstream.core.Configuration.artifactGroup,
        artifactId = "stream-android-core-lint",
        version = rootProject.version.toString()
    )
    configure(
        KotlinJvm(
            javadocJar = JavadocJar.Dokka("dokkaJavadoc"),
            sourcesJar = true,
        )
    )
}
