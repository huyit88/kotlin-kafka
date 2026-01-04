package com.example

import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class AvroTxConsumer {
    private val logger = LoggerFactory.getLogger(AvroTxConsumer::class.java)

    @KafkaListener(
        topics = ["transactions-avro"],
        groupId = "fraud-avro",
        containerFactory = "avroKafkaListenerContainerFactory"
    )
    fun onAvroEvent(record: ConsumerRecord<String, GenericRecord>) {
        val avroRecord = record.value()
        val transactionId = avroRecord.get("transactionId")?.toString() ?: "unknown"
        val amount = avroRecord.get("amount") as? Double ?: 0.0
        val currency = avroRecord.get("currency")?.toString() ?: "unknown"
        // merchantId is optional - works with both v1 (null) and v2 (present) records
        val merchantId = avroRecord.get("merchantId")?.toString() ?: "null"
        
        logger.info("AVRO_TX consumed id=$transactionId amount=$amount currency=$currency merchantId=$merchantId")
    }
}

