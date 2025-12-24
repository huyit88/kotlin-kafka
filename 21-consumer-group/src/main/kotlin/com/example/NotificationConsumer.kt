package com.example

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class NotificationConsumer {
    private val logger = LoggerFactory.getLogger(NotificationConsumer::class.java)

    @KafkaListener(topics = ["\${kafka.topic.notifications}"], groupId = "\${spring.kafka.consumer.group-id}")
    fun onEvent(record: ConsumerRecord<String, String>) {
        val value = record.value()
        Thread.sleep(800)

        val instanceId = System.getenv("INSTANCE_ID") ?: "local-1"
        logger.info("instance=$instanceId partition=${record.partition()} message=$value")
    }
}

