package com.example

import org.springframework.http.HttpStatus
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.ResponseStatus

@RestController
@RequestMapping
class PaymentsReplayController(
    private val kafkaTemplate: KafkaTemplate<String, String>
) {
    @PostMapping("/payments/replay/{paymentId}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun replayPayment(
        @PathVariable paymentId: String,
        @RequestBody req: PaymentReplayRequest
    ) {
        kafkaTemplate.send("payments", paymentId, "${paymentId},${req.userId},${req.amount}")
    }
}