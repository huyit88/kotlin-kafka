package com.example

import org.apache.kafka.clients.consumer.ConsumerGroupMetadata
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class FraudPipeline(
    private val kafkaTemplate: KafkaTemplate<String, String>
) {
    private val logger = LoggerFactory.getLogger(FraudPipeline::class.java)

    @KafkaListener(topics = ["payments"])
    fun onPaymentEvent(records: List<ConsumerRecord<String, String>>) {
        try {
            kafkaTemplate.executeInTransaction { kt ->
                logger.info("TX_BEGIN batchSize=${records.size}")
                val offsets = mutableMapOf<TopicPartition, OffsetAndMetadata>()
                
                for (record in records) {
                    val isFraud = record.value().contains("FRAUD")

                    if (!isFraud) {
                        kt.send("payments-validated", record.key(), record.value())
                    }                    

                    val topicPartition = TopicPartition(record.topic(), record.partition())
                    offsets[topicPartition] = OffsetAndMetadata(record.offset() + 1)
                }

                // Check for FAIL_AFTER_PRODUCE env var (uppercase, as per command example)
                val failAfterProduce = System.getenv("FAIL_AFTER_PRODUCE")?.toBoolean() ?: false
                if (failAfterProduce) {
                    throw RuntimeException("simulate fail after produce")
                }
                
                val groupMetadata = ConsumerGroupMetadata("fraud-detector")
                kt.sendOffsetsToTransaction(offsets, groupMetadata)
                val offsetsStr = offsets.entries.joinToString(", ") { 
                    "${it.key.topic()}-${it.key.partition()}:${it.value.offset()}" 
                }
                logger.info("TX_COMMIT offsets={$offsetsStr}")
            }
        } catch (e: Exception) {
            logger.error("TX_ABORT reason=${e.message}", e)
            throw e
        }
    }
}

