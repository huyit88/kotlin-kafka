package com.example

import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service

@Service
class PaymentService(
    private val kafkaTemplate: KafkaTemplate<String, String>
) {
    private val logger = LoggerFactory.getLogger(PaymentService::class.java)

    fun processPayment(paymentId: String, amount: Double, fail: Boolean) {
        kafkaTemplate.executeInTransaction { kt ->
            logger.info("PAYMENT_BEGIN id=$paymentId")
            kt.send("payments", paymentId, "PAYMENT:$paymentId:$amount")
            kt.send("payment-audit", paymentId, "AUDIT:$paymentId")

            if (fail) {
                logger.info("PAYMENT_ABORT id=$paymentId")
                throw RuntimeException("simulate failure")
            }
            logger.info("PAYMENT_COMMIT id=$paymentId")
        }
    }
}

