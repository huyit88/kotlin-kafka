package com.example

data class PaymentRequest(
    val paymentId: String,
    val userId: String,
    val amount: Long
)