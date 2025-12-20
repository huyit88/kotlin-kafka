package com.example

import org.springframework.kafka.core.KafkaTemplate
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping
class PaymentProducerController(
    private val kafkaTemplate: KafkaTemplate<String, String>
) {
    @PostMapping("/payments")
    fun payment(@RequestBody req: PaymentRequest) {
        kafkaTemplate.send("payments", req.paymentId, "${req.paymentId},${req.userId},${req.amount}")
    }
}