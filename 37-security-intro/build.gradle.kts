// This module inherits configuration from the parent build.gradle.kts
// Add any module-specific dependencies or configurations here if needed

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kafka.clients)
    testImplementation(project(":common-testutils"))
    testImplementation(libs.kotlin.test)
}

tasks.withType<Test>().configureEach {
  testLogging {
    showStandardStreams = true
    events("passed", "skipped", "failed", "standardOut", "standardError")
  }
  outputs.upToDateWhen { false }
}
