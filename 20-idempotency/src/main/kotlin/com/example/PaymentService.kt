package com.example

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class PaymentService(
    private val paymentRepo: ProcessedPaymentRepository,
    private val userRepo: UserBalanceRepository,
) {
    private val logger = LoggerFactory.getLogger(PaymentService::class.java)

    @Transactional
    fun processPayment(paymentId: String, userId: String, amount: Long) {
        if (paymentRepo.existsById(paymentId)) {
            logger.info("Duplicate payment detected, skipping: $paymentId")
            return
        }

        logger.info("Processing NEW payment: $paymentId")
        println("Processing payment: $paymentId for user: $userId, amount: $amount")
        val processedPayment = ProcessedPayment(paymentId, userId, amount, Instant.now())
        paymentRepo.save(processedPayment)

        // Update user balance
        val userBalance = userRepo.findById(userId).orElse(UserBalance(userId, 0L))
        val updated = userBalance.copy(balance = userBalance.balance + amount)
        userRepo.save(updated)
    }
}