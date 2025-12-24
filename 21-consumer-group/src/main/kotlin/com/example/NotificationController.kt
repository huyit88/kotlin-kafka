package com.example

import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.kafka.core.KafkaTemplate

@RestController
@RequestMapping("/notify")
class NotificationController(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    @Value("\${kafka.topic.notifications}") private val notificationsTopic: String
) {
    @PostMapping()
    fun notify(@RequestBody req: NotificationRequest) {
        val value = "${req.userId},${req.channel},${req.message}"
        kafkaTemplate.send(notificationsTopic, req.userId, value)
    }
}
