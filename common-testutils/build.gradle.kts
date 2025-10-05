plugins {
}

dependencies {
    implementation(libs.kafka.clients)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.testcontainers.kafka)
    testImplementation(libs.testcontainers.junitJupiter)
}

