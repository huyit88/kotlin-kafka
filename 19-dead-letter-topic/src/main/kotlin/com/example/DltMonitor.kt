package com.example
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.errors.SerializationException
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class DltMonitor{
    val logger = LoggerFactory.getLogger(DltMonitor::class.java)
    @KafkaListener(topics = ["payments.DLT"], groupId = "dlt-monitor")
    fun onEvent(record: ConsumerRecord<String, String>){
        val value = record.value()
        println("⚠️ DLT received: $value")
        logger.info("⚠️ DLT received: $value")
    }
}

