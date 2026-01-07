package com.example

data class TxRequest(
    val paymentId: String,
    val amount: Double,
    val fail: Boolean
)

