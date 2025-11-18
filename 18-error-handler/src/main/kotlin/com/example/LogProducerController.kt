package com.example

import org.springframework.http.ResponseEntity
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping
class LogProducerController(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val binaryKafkaTemplate: KafkaTemplate<String, ByteArray>
){
    @PostMapping("/produce/{msg}")
    fun createMessage(@PathVariable msg: String): ResponseEntity<String> {
        kafkaTemplate.send("my-topic", msg)
        return ResponseEntity.ok("Message sent: $msg")
    }
}