// This module inherits configuration from the parent build.gradle.kts
// Add any module-specific dependencies or configurations here if needed

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.kafka:spring-kafka")  
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kafka.clients)
    implementation(libs.kotlin.reflect)
    testImplementation(project(":common-testutils"))
    testImplementation(libs.kotlin.test)
    runtimeOnly("com.h2database:h2")
}

tasks.withType<Test>().configureEach {
  testLogging {
    showStandardStreams = true
    events("passed", "skipped", "failed", "standardOut", "standardError")
  }
  outputs.upToDateWhen { false }
}


plugins {
    alias(libs.plugins.kotlin.spring.jpa)
}

/**
noArg {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.Embeddable")
    annotation("jakarta.persistence.MappedSuperclass")
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.Embeddable")
    annotation("jakarta.persistence.MappedSuperclass")
}