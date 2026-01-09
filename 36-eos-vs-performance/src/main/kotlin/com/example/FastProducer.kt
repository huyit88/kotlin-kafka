package com.example

import org.springframework.stereotype.Component
import org.springframework.kafka.core.KafkaTemplate
import kotlin.system.measureTimeMillis

@Component
class FastProducer(
    private val kafkaTemplateFast: KafkaTemplate<String, String>
) {
    private val TOPIC = "payments-fast"

    fun sendMessage(n: Int) {
        val durationMs = measureTimeMillis {
            // Send all messages asynchronously (fire and forget)
            for (i in 1..n) {
                kafkaTemplateFast.send(TOPIC, "key-$i", "payment-$i")
            }
            // Flush to ensure all messages are sent (with acks=1, this is faster than acks=all)
            kafkaTemplateFast.flush()
        }
        println("BENCHMARK type=FAST count=$n durationMs=$durationMs")
    }
}