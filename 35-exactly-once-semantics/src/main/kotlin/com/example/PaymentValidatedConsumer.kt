package com.example

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class PaymentValidatedConsumer {
    private val logger = LoggerFactory.getLogger(PaymentValidatedConsumer::class.java)

    @KafkaListener(
        topics = ["payments-validated"],
        containerFactory = "readCommittedKafkaListenerContainerFactory"
    )
    fun onPaymentEvent(record: ConsumerRecord<String, String>) {
        val value = record.value()
        logger.info("RC value=$value")
    }
}

