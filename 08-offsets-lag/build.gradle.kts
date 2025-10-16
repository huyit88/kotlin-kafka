plugins {
    application
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kafka.clients)
}

tasks.withType<Test>().configureEach {
  testLogging {
    showStandardStreams = true
    events("passed", "skipped", "failed", "standardOut", "standardError")
  }
  outputs.upToDateWhen { false }
}

application {
    mainClass.set(providers.gradleProperty("mainClass").orElse("com.example.LagInspectorKt"))
}