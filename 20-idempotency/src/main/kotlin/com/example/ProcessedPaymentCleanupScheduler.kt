package com.example

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class ProcessedPaymentCleanupScheduler(
    private val repository: ProcessedPaymentRepository,
    private val properties: IdempotencyCleanupProperties
) {
    private val logger = LoggerFactory.getLogger(ProcessedPaymentCleanupScheduler::class.java)

    @Scheduled(fixedRateString = "\${idempotency.cleanup.interval-ms:60000}")
    @Transactional
    fun cleanupOldProcessedPayments() {
        val threshold = Instant.now().minusSeconds(properties.ttlMinutes * 60)
        val countBefore = repository.count()
        repository.deleteByProcessedAtBefore(threshold)
        val countAfter = repository.count()
        val deleted = countBefore - countAfter
        
        if (deleted > 0) {
            logger.info("Cleaned up $deleted old processed payment(s) older than ${properties.ttlMinutes} minute(s)")
        }
    }
}
