// This module inherits configuration from the parent build.gradle.kts
// Add any module-specific dependencies or configurations here if needed

plugins {
    alias(libs.plugins.kotlin.serialization)
    application
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kafka.clients)
    testImplementation(project(":common-testutils"))
    testImplementation(libs.testcontainers.junitJupiter)
    testImplementation(libs.testcontainers.kafka)
    testImplementation(libs.kotlin.test)
}

application {
    // Use a safe application name for generated scripts (must not start with a digit)
    applicationName = "logging-producer"
    // change if your package differs
    mainClass.set("com.example.MainKt")
}

// Forward stdin to the application when using `gradlew run`
tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}