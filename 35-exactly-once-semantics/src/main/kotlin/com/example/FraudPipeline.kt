package com.example

import org.apache.kafka.clients.consumer.ConsumerGroupMetadata
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class FraudPipeline(
    private val kafkaTemplate: KafkaTemplate<String, String>
) {
    private val logger = LoggerFactory.getLogger(FraudPipeline::class.java)
    private val processedPaymentIds = ConcurrentHashMap<String, Boolean>()

    @KafkaListener(topics = ["payments"])
    fun onPaymentEvent(records: List<ConsumerRecord<String, String>>) {
        try {
            val crashPoint = System.getenv("CRASH_POINT") ?: "None"
            logger.info("CRASH_POINT=$crashPoint")
            kafkaTemplate.executeInTransaction { kt ->
                logger.info("TX_BEGIN batchSize=${records.size}")
                val offsets = mutableMapOf<TopicPartition, OffsetAndMetadata>()
                
                for (record in records) {
                    val paymentId = record.key()
                    val isFraud = record.value().contains("FRAUD")
                    val isDuplicated = processedPaymentIds.containsKey(paymentId)
                    if(!isDuplicated){
                        processedPaymentIds[paymentId] = true
                    }else{
                        logger.info("DEDUPE_DROP id=$paymentId")
                    }

                    if (!isFraud && !isDuplicated) {
                        kt.send("payments-validated", paymentId, record.value())
                    }        

                    val topicPartition = TopicPartition(record.topic(), record.partition())
                    offsets[topicPartition] = OffsetAndMetadata(record.offset() + 1)
                }

                if (crashPoint == "afterProduce") {
                    throw RuntimeException("simulate fail after produce")
                }
                
                val groupMetadata = ConsumerGroupMetadata("fraud-detector")
                kt.sendOffsetsToTransaction(offsets, groupMetadata)
                val offsetsStr = offsets.entries.joinToString(", ") { 
                    "${it.key.topic()}-${it.key.partition()}:${it.value.offset()}" 
                }
                logger.info("TX_COMMIT offsets={$offsetsStr}")
                if (crashPoint == "afterOffsets") {
                    throw RuntimeException("simulate fail after offsets")
                }
            }
        } catch (e: Exception) {
            logger.error("TX_ABORT reason=${e.message}", e)
            throw e
        }
    }
}

