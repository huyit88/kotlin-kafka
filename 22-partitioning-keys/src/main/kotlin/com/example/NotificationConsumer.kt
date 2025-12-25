package com.example

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class NotificationConsumer(
    private val hotMetricsService: HotMetricsService
) {
    private val logger = LoggerFactory.getLogger(NotificationConsumer::class.java)
    private val lastSeqByUserId = ConcurrentHashMap<String, Int>()

    @KafkaListener(topics = ["\${kafka.topic.notifications}"], groupId = "\${spring.kafka.consumer.group-id}")
    fun onEvent(record: ConsumerRecord<String, String>) {
        val value = record.value()
        Thread.sleep(200)

        // Parse value: "userId,seq,channel,message"
        val parts = value.split(",")
        if (parts.size < 2) {
            logger.warn("Invalid message format: $value")
            return
        }
        
        val userId = record.key() ?: parts[0]
        val seq = parts[1].toIntOrNull() ?: run {
            logger.warn("Invalid seq in message: $value")
            return
        }
        
        val lastSeq = lastSeqByUserId[userId]
        if (lastSeq != null && seq <= lastSeq) {
            logger.info("❌ OUT_OF_ORDER userId=$userId prev=$lastSeq now=$seq partition=${record.partition()}")
        }
        
        lastSeqByUserId[userId] = seq
        
        // Track HOT messages for metrics
        if (userId == "HOT") {
            hotMetricsService.incrementHotCount()
        }
        
        logger.info("key=$userId partition=${record.partition()} seq=$seq")
    }
}

