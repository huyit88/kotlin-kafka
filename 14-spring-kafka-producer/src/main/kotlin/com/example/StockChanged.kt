package com.example

data class StockChanged(
      val eventId: String,
      val sku: String,
      val delta: Int,
      val reason: String,
      val occurredAt: Long = System.currentTimeMillis()
  )