import org.gradle.api.plugins.JavaPluginExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  alias(libs.plugins.kotlin.jvm) apply false
  alias(libs.plugins.kotlin.spring) apply false
  alias(libs.plugins.spring.boot) apply false
  alias(libs.plugins.spring.dep.mgmt) apply false
}


allprojects {
  group = "com.example"
  version = "0.0.1-SNAPSHOT"
  repositories { 
    mavenCentral()
    maven {
      url = uri("https://packages.confluent.io/maven/")
    }
  }
}

subprojects {
  apply(plugin = "org.jetbrains.kotlin.jvm")
  apply(plugin = "org.jetbrains.kotlin.plugin.spring")
  apply(plugin = "org.springframework.boot")
  apply(plugin = "io.spring.dependency-management")

  configure<JavaPluginExtension> {
    // Use the current Java version
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }

  tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
      jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
  }

  tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
      showStandardStreams = true
      events("passed", "skipped", "failed", "standardOut", "standardError")
    }
  }

  afterEvaluate {
    dependencies {
      "implementation"(libs.spring.boot.starter.web)
      "testImplementation"(libs.spring.boot.starter.test)
    }
  }
}

project(":common-testutils") {
  plugins.apply("org.jetbrains.kotlin.jvm")
  // no Spring Boot plugin for test utils
}
