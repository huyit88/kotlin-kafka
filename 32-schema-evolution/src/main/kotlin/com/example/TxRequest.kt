package com.example

data class TxRequest(
    val transactionId: String,
    val amount: Double,
    val currency: String,
    val merchantId: String? = null
)

