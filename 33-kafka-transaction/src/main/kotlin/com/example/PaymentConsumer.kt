package com.example

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class PaymentConsumer {
    private val logger = LoggerFactory.getLogger(PaymentConsumer::class.java)

    @KafkaListener(topics = ["payments"])
    fun onPaymentEvent(record: ConsumerRecord<String, String>) {
        val value = record.value()
        logger.info("CONSUMED topic=payments value=$value")
    }

    @KafkaListener(topics = ["payment-audit"])
    fun onPaymentAuditEvent(record: ConsumerRecord<String, String>) {
        val value = record.value()
        logger.info("CONSUMED topic=payment-audit value=$value")
    }
}

