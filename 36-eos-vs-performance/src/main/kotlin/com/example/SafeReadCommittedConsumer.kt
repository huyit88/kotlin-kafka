package com.example

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class SafeReadCommittedConsumer {
    private val logger = LoggerFactory.getLogger(SafeReadCommittedConsumer::class.java)

    @KafkaListener(
        topics = ["payments-safe"],
        groupId = "safe-read-committed",
        containerFactory = "readCommittedKafkaListenerContainerFactory"
    )
    fun onPaymentEvent(record: ConsumerRecord<String, String>) {
        val value = record.value()
        val eventTime = MessageUtils.extractEventTime(value)
        val lagMs = MessageUtils.calculateLag(eventTime)
        logger.info("CONSUMED_RC ts=$eventTime lagMs=$lagMs")
    }
}

