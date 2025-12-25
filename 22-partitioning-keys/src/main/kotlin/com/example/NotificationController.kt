package com.example

import org.apache.kafka.common.utils.Utils
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.kafka.core.KafkaTemplate

@RestController
@RequestMapping
class NotificationController(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    @Value("\${kafka.topic.notifications}") private val notificationsTopic: String,
    @Value("\${kafka.topic.partitions}") private val partitionCount: Int,
    private val hotMetricsService: HotMetricsService
) {
    @PostMapping("/notify")
    fun notify(@RequestBody req: NotificationRequest) {
        val userId = req.userId ?: throw IllegalArgumentException("userId is required for /notify endpoint")
        val value = "$userId,${req.seq},${req.channel},${req.message}"
        kafkaTemplate.send(notificationsTopic, userId, value)
    }

    @PostMapping("/notify/hot")
    fun notifyHot(@RequestBody req: NotificationRequest) {
        val value = "HOT,${req.seq},${req.channel},${req.message}"
        kafkaTemplate.send(notificationsTopic, "HOT", value)
    }

    @PostMapping("/notify/no-key")
    fun notifyNoKey(@RequestBody req: NotificationRequest) {
        val userId = req.userId ?: throw IllegalArgumentException("userId is required for /notify/no-key endpoint")
        val value = "$userId,${req.seq},${req.channel},${req.message}"
        kafkaTemplate.send(notificationsTopic, null, value)
    }

    @GetMapping("/debug/partition/{userId}")
    fun debug(@PathVariable userId: String): Map<String, Int> {
        // Serialize the key using StringSerializer (same as producer)
        val serializer = StringSerializer()
        val keyBytes = serializer.serialize(notificationsTopic, userId)
        
        // Calculate partition using Kafka's murmur2 hash + mod (same logic as producer)
        val partition = Utils.toPositive(Utils.murmur2(keyBytes)) % partitionCount
        
        return mapOf("partition" to partition)
    }

    @GetMapping("/metrics/hot")
    fun metrics(): Map<String, Any> {
        return mapOf(
            "count" to hotMetricsService.getCount(),
            "rate" to String.format("%.2f", hotMetricsService.getRate())
        )
    }

}
