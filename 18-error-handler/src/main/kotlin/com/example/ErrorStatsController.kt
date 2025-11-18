package com.example

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class ErrorStatsController(
    private val registry: MeterRegistry,
    private val dltProbeService: DltProbeService
){
    @GetMapping("/stats/errors")
    fun getErrors(): Map<String, Double> {
        val retriedCounter = registry.counter("kafka.errors.retried")
        val dltCounter = registry.counter("kafka.errors.dlt")
        return mapOf(
            "retried" to retriedCounter.count(),
            "dlt" to dltCounter.count()
        )
    }
    
    @GetMapping("/stats/headers")
    fun getHeaders(): Map<String, String> {
        return dltProbeService.getLatestDltRecordHeaders()
    }

    @GetMapping("/stats/last-dlt")
    fun getLastDlt(): Map<String, Any> {
        return dltProbeService.getLatestDltRecord()
    }
}