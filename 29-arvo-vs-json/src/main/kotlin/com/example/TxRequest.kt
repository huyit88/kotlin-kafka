package com.example

data class TxRequest(
    val transactionId: String,
    val currency: String,
    val amount: Double,
    val merchantId: String? = null  // Optional to match Avro schema evolution (v2 field)
)