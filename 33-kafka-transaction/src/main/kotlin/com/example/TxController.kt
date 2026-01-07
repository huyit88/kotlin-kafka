package com.example

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class PaymentController(
    private val paymentService: PaymentService
) {
    @PostMapping("/pay")
    fun pay(@RequestBody req: TxRequest) {
        paymentService.processPayment(req.paymentId, req.amount, req.fail)
    }
}