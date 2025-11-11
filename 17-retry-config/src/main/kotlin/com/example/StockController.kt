package com.example

import org.springframework.web.bind.annotation.*
import java.util.UUID
import kotlin.system.measureTimeMillis

@RestController
@RequestMapping("/api/stock")
class StockController(
    private val stockProducer: StockProducerTyped
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

    @PostMapping("/bulkRetry")
    fun bulk(@RequestParam sku: String, @RequestParam n: Int, @RequestParam mode: String): Map<String,Any>{
        require(mode in listOf("sync", "async")) { "mode must be 'sync' or 'async'" }
        
        val totalTime = measureTimeMillis {
            for(i in 1..n){
                val event = StockChanged(
                    eventId = UUID.randomUUID().toString(),
                    sku = sku,
                    delta = i,
                    reason = "RESERVE"
                )
                stockProducer.send(event)
            }
        }
        val rps = if (totalTime > 0) (n * 1000.0 / totalTime).toInt() else n
        return mapOf(
            "mode" to mode,
            "sent" to n, 
            "ms" to totalTime, 
            "rps" to rps,
            "note" to "Check consumer logs for processing throughput"
        )
    }
}