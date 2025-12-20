package com.example

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class PaymentConsumer(
    private val paymentService: PaymentService
) {
    private val logger = LoggerFactory.getLogger(PaymentConsumer::class.java)

    @KafkaListener(topics = ["payments"], groupId = "payments-consumer")
    fun onEvent(record: ConsumerRecord<String, String>) {
        val value = record.value()
        logger.info("📥 Received from Kafka: $value [key=${record.key()}, partition=${record.partition()}, offset=${record.offset()}]")
        
        val attrs = value.split(",")
        if (attrs.size != 3) {
            logger.error("Invalid payment format: $value")
            return
        }
        
        val paymentId = attrs[0]
        val userId = attrs[1]
        val amount = attrs[2].toLong()
        
        paymentService.processPayment(paymentId, userId, amount)
    }
}

