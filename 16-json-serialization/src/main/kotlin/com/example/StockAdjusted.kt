package com.example

data class StockAdjusted(
    val eventId: String, 
    val sku: String, 
    val amount: Int, 
    val reason: String ="ADJUST", 
    val occurredAt: Long = System.currentTimeMillis()
)