package com.example

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.header.internals.RecordHeader
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer

class CustomDeadLetterRecoverer(
    private val kafkaTemplate: KafkaTemplate<String, String>
) : DeadLetterPublishingRecoverer(
    kafkaTemplate,
    { record, ex -> TopicPartition("${record.topic()}.DLT", record.partition()) }
) {
    override fun accept(record: ConsumerRecord<*, *>, ex: Exception) {
        // Determine error type based on exception type
        val errorType = when (ex) {
            is RuntimeException -> "runtime"
            else -> "unknown"
        }
        
        // Get original message value (not exception message)
        val originalValue = record.value() as? String ?: ""
        
        // Create DLT topic partition
        val dltTopicPartition = TopicPartition("${record.topic()}.DLT", record.partition())
        
        // Create producer record with custom headers
        val producerRecord = ProducerRecord<String, String>(
            dltTopicPartition.topic(),
            dltTopicPartition.partition(),
            record.key() as? String,
            originalValue
        )


        val errorCategory = when (originalValue) {
            "reject" -> "validation"
            "fail" -> "business"
            else -> "unexpected"
        }

        val headers = producerRecord.headers()
        
        // Copy existing headers from original record
        record.headers().forEach { header ->
            headers.add(header)
        }
        
        // Add custom headers
        headers.add(RecordHeader("x-error-type", errorType.toByteArray()))
        headers.add(RecordHeader("x-original-value", originalValue.toByteArray()))
        headers.add(RecordHeader("x-error-category", errorCategory.toByteArray()))
        
        // Send to DLT and wait for completion
        kafkaTemplate.send(producerRecord).get()
    }
}

