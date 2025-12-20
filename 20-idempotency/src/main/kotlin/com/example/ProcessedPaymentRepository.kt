package com.example

import org.springframework.data.repository.CrudRepository
import java.time.Instant

interface ProcessedPaymentRepository : CrudRepository<ProcessedPayment, String> {
    fun deleteByProcessedAtBefore(threshold: Instant)
}