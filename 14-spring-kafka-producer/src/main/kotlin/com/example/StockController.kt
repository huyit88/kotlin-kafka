package com.example

import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/stock")
class StockController(
    private val stockProducer: StockProducer
){
    @PostMapping("/reserve")
    fun reserve(@RequestParam sku: String, @RequestParam qty: Int): Map<String,Any>{
        val event = StockChanged(
            eventId = UUID.randomUUID().toString(),
            sku = sku,
            delta = -qty,
            reason = "RESERVE"
        )
        stockProducer.send(event)
        return mapOf("status" to "OK", "eventId" to event.eventId)
    }

    @PostMapping("/release")
    fun release(@RequestParam sku: String, @RequestParam qty: Int): Map<String,Any>{
        val event = StockChanged(
            eventId = UUID.randomUUID().toString(),
            sku = sku,
            delta = qty,
            reason = "RELEASE"
        )
        stockProducer.send(event)
        return mapOf("status" to "OK", "eventId" to event.eventId)
    }

    @PostMapping("/bulk")
    fun bulk(@RequestParam sku: String, @RequestParam n: Int): Map<String,Any>{
        val start = System.currentTimeMillis()
        for(i in 1..n){
            val event = StockChanged(
                eventId = UUID.randomUUID().toString(),
                sku = sku,
                delta = -1,
                reason = "RESERVE"
            )
            stockProducer.send(event)
        }
        val end = System.currentTimeMillis()
        val totalTime = end - start
        val rps = if (totalTime > 0) (n * 1000.0 / totalTime).toInt() else n
        return mapOf("sent" to n, "ms" to totalTime, "rps" to rps)
    }
}