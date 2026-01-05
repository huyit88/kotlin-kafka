package com.example

data class TxRequest(
    val transactionId: String,
    val currency: String,
    val amount: Double
)