plugins {
    alias(libs.plugins.jetbrains.kotlin.jvm)
    alias(libs.plugins.stream.java.library)
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
