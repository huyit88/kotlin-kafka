package com.example

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "PROCESSED_PAYMENT")
data class ProcessedPayment(
    @Id
    val paymentId: String,
    val userId: String,
    val amount: Long,
    val processedAt: Instant
)