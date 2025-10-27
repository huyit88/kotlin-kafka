package com.example

import org.springframework.stereotype.Component
import org.springframework.boot.CommandLineRunner
import org.springframework.kafka.core.KafkaTemplate

@Component
class StartupProducer(
    private val template: KafkaTemplate<String, String>
) : CommandLineRunner {
    override fun run(vararg args: String?) {
        template.send("inventory.init", "app-started", "Inventory service online")
        println("Sent test message to Kafka")
    }
}