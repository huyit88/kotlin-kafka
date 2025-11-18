package com.example

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.errors.SerializationException
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
import org.springframework.stereotype.Component

@Component
class LogConsumer{
    private val logger = LoggerFactory.getLogger(LogConsumer::class.java)

    @KafkaListener(topics = ["my-topic"], groupId = "my-topic-consumer")
    fun handler(record: ConsumerRecord<String, String>){
        val message = record.value()
        
        // If value is null, it means ErrorHandlingDeserializer caught a deserialization error
        // The error handler will send it to DLT, so we should not process it here
        if (message == null) {
            logger.warn("Received null value - deserialization error was caught by ErrorHandlingDeserializer")
            // Don't process null values - they will be handled by the error handler and sent to DLT
            return
        }
        
        // Simulate deserialization error for invalid binary data
        if (message.contains("invalid-binary-data")) {
            throw SerializationException("Invalid binary data detected")
        }

        if(message.contains("bad-arg")){
            throw IllegalArgumentException("Bad argument")
        }
        
        if(message.contains("fail")){
            throw RuntimeException("unexpected error")
        }
        logger.info("${Thread.currentThread().name} get ${record.key()} = $message")
    }
}