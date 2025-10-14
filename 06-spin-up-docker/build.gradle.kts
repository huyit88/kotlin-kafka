plugins {
    application
}

// This module inherits configuration from the parent build.gradle.kts
// Add any module-specific dependencies or configurations here if needed

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kafka.clients)
}

application {
    // Kotlin top-level main in CpuMetricsProducer.kt compiles to CpuMetricsProducerKt
    mainClass.set(providers.gradleProperty("mainClass").orElse("com.example.CpuMetricsProducerKt"))
}
