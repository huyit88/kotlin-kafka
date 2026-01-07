package com.example

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class DirtyPaymentConsumer {
    private val logger = LoggerFactory.getLogger(DirtyPaymentConsumer::class.java)

    @KafkaListener(
        topics = ["payments"],
        containerFactory = "dirtyKafkaListenerContainerFactory"
    )
    fun onPaymentEvent(record: ConsumerRecord<String, String>) {
        val value = record.value()
        logger.info("DIRTY_READ topic=payments value=$value")
    }

    @KafkaListener(
        topics = ["payment-audit"],
        containerFactory = "dirtyKafkaListenerContainerFactory"
    )
    fun onPaymentAuditEvent(record: ConsumerRecord<String, String>) {
        val value = record.value()
        logger.info("DIRTY_READ topic=payment-audit value=$value")
    }
}

