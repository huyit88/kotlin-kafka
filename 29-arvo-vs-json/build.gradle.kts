// This module inherits configuration from the parent build.gradle.kts
// Add any module-specific dependencies or configurations here if needed

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.kafka:spring-kafka")  
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kafka.clients)
    implementation("io.confluent:kafka-avro-serializer:7.6.0")
    implementation("org.apache.avro:avro:1.11.3")
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
