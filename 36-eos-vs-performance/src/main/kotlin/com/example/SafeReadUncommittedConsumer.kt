package com.example

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class SafeReadUncommittedConsumer {
    private val logger = LoggerFactory.getLogger(SafeReadUncommittedConsumer::class.java)

    @KafkaListener(
        topics = ["payments-safe"],
        groupId = "safe-read-uncommitted",
        containerFactory = "readUncommittedKafkaListenerContainerFactory"
    )
    fun onPaymentEvent(record: ConsumerRecord<String, String>) {
        val value = record.value()
        val eventTime = MessageUtils.extractEventTime(value)
        val lagMs = MessageUtils.calculateLag(eventTime)
        logger.info("CONSUMED_RU ts=$eventTime lagMs=$lagMs")
    }
}

