
plugins {
    application
}

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


application {
    // Kotlin top-level main in CpuMetricsProducer.kt compiles to CpuMetricsProducerKt
    mainClass.set(providers.gradleProperty("mainClass"))
}
