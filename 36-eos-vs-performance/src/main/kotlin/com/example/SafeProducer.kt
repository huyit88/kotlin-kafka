package com.example

import org.springframework.stereotype.Component
import org.springframework.kafka.core.KafkaTemplate
import kotlin.system.measureTimeMillis

@Component
class SafeProducer(
    private val kafkaTemplateSafe: KafkaTemplate<String, String>
) {
    private val TOPIC = "payments-safe"

    fun sendMessage(n: Int) {
        val durationMs = measureTimeMillis {
            // Execute in transaction for EOS semantics
            kafkaTemplateSafe.executeInTransaction { operations ->
                for (i in 1..n) {
                    val eventTime = System.currentTimeMillis()
                    val value = "payment-$i|eventTime=$eventTime"
                    operations.send(TOPIC, "key-$i", value)
                }
            }
        }
        println("BENCHMARK type=SAFE count=$n durationMs=$durationMs")
    }
}