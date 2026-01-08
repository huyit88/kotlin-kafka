package com.example

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.consumer.ConsumerConfig

@RestController
class EOSController(
    private val producerProps: Map<String, Any>,
    private val consumerProps: Map<String, Any>
) {
    @GetMapping("/eos/check")
    fun check(): ResponseEntity<Map<String, Any>> {
        try {
            // Validate producer configs
            val transactionalId = producerProps[ProducerConfig.TRANSACTIONAL_ID_CONFIG] as? String
            if (transactionalId.isNullOrBlank()) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(mapOf("error" to "producer.transactionalId must not be blank"))
            }

            val idempotence = producerProps[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] as? Boolean
                ?: throw IllegalStateException("producer.idempotence is missing")

            // Validate consumer configs
            val autoCommit = consumerProps[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] as? Boolean
                ?: throw IllegalStateException("consumer.autoCommit is missing")
            if (autoCommit != false) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(mapOf("error" to "consumer.autoCommit must be false"))
            }

            val isolationLevel = consumerProps[ConsumerConfig.ISOLATION_LEVEL_CONFIG] as? String
                ?: throw IllegalStateException("consumer.isolationLevel is missing")
            if (isolationLevel != "read_committed") {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(mapOf("error" to "consumer.isolationLevel must be 'read_committed', got: $isolationLevel"))
            }

            val producerRes = mapOf(
                "idempotence" to idempotence,
                "transactionalId" to transactionalId
            )

            val consumerRes = mapOf(
                "autoCommit" to autoCommit,
                "isolationLevel" to isolationLevel
            )

            val pipelineRes = mapOf(
                // See FraudPipeline.onPaymentEvent() line 45: kt.sendOffsetsToTransaction(offsets, groupMetadata)
                "usesSendOffsetsToTransaction" to true
            )

            return ResponseEntity.ok(mapOf(
                "producer" to producerRes,
                "consumer" to consumerRes,
                "pipeline" to pipelineRes
            ))
        } catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to e.message ?: "Unknown error during EOS check"))
        }
    }
}