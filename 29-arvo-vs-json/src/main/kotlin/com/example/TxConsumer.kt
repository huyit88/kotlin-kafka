package com.example

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class TxConsumer(
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(TxConsumer::class.java)

    @KafkaListener(topics=["transactions-json"], groupId="fraud-json")
    fun onEvent(record: ConsumerRecord<String, String>) {
        val jsonString = record.value()
        val tx = objectMapper.readValue(jsonString, TxRequest::class.java)
        logger.info("JSON_TX id=${tx.transactionId} amount=${tx.amount} currency=${tx.currency}")
    }
}

