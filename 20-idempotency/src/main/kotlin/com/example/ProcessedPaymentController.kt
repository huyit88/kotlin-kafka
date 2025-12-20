package com.example

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/payments")
class ProcessedPaymentController(
    private val repository: ProcessedPaymentRepository
) {
    @GetMapping("/processed")
    fun getProcessedCount(): Map<String, Long> {
        val count = repository.count()
        return mapOf("count" to count)
    }
}
