package com.example

import org.springframework.web.bind.annotation.*
import org.springframework.http.ResponseEntity
import org.springframework.kafka.core.KafkaTemplate

@RestController
class PaymentProducerController(
    private val kafkaTemplate: KafkaTemplate<String, String>
){
    @PostMapping("/pay/{data}")
    fun pay(@PathVariable data: String): ResponseEntity<String>{
        kafkaTemplate.send("payments", data)
        return ResponseEntity.ok("Message sent: $data")
    }
}