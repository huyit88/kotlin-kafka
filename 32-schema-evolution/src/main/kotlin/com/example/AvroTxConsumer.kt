package com.example

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class AvroTxConsumer {
    private val logger = LoggerFactory.getLogger(AvroTxConsumer::class.java)

    @KafkaListener(
        topics = ["transactions-avro"],
        groupId = "fraud-detector",
        containerFactory = "avroKafkaListenerContainerFactory"
    )
    fun onAvroEvent(record: ConsumerRecord<String, Transaction>) {
        val tx = record.value()
        // Type-safe access - no casting, no string field names, no hardcoded schema
        logger.info("AVRO_CONSUMED id=${tx.transactionId} amount=${tx.amount} currency=${tx.currency}")
    }
}

