package com.example

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.errors.SerializationException
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class PaymentConsumer{
    val logger = LoggerFactory.getLogger(PaymentConsumer::class.java)

    @KafkaListener(topics = ["payments"], groupId = "payment-consumers")
    fun onEvent(record: ConsumerRecord<String, String>){
        val value = record.value()
        if (value == "fail") {
            throw RuntimeException("Payment processing failed for: $value")
        }
        if(value == "reject"){
            throw IllegalArgumentException("Payment processing rejected for: $value")
        }
        logger.info("Payment processed: $value [p=${record.partition()} off=${record.offset()}]")
    }

}

