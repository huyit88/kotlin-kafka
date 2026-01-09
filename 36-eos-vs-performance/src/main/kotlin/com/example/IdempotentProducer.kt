package com.example

import org.springframework.stereotype.Component
import org.springframework.kafka.core.KafkaTemplate
import kotlin.system.measureTimeMillis

@Component
class IdempotentProducer(
    private val kafkaTemplateIdempotence: KafkaTemplate<String, String>
) {
    private val TOPIC = "payments-idempotent"

    fun sendMessage(n: Int) {
        val durationMs = measureTimeMillis {
            // Send all messages asynchronously (idempotent, no transactions)
            for (i in 1..n) {
                kafkaTemplateIdempotence.send(TOPIC, "key-$i", "payment-$i")
            }
            // Flush to ensure all messages are sent
            // Note: acks=all is required for idempotence, so slower than FAST but faster than EOS
            kafkaTemplateIdempotence.flush()
        }
        println("BENCHMARK type=IDEMPOTENT count=$n durationMs=$durationMs")
    }
}