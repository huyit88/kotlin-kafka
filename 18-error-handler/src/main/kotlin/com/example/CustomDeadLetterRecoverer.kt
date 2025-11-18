package com.example

import io.micrometer.core.instrument.Counter
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.header.internals.RecordHeader
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer

class CustomDeadLetterRecoverer(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val dltCounter: Counter
) : DeadLetterPublishingRecoverer(
    kafkaTemplate,
    { record, ex -> TopicPartition("${record.topic()}.DLT", record.partition()) }
) {
    override fun accept(record: ConsumerRecord<*, *>, ex: Exception) {
        // Increment DLT counter
        dltCounter.increment()
        
        // Determine error category based on exception type
        val errorCategory = when (ex) {
            is SerializationException -> "deserialization"
            is RuntimeException, is IllegalStateException -> "business"
            else -> "unexpected"
        }
        
        // Create DLT topic partition
        val dltTopicPartition = TopicPartition("${record.topic()}.DLT", record.partition())
        
        // Create producer record with custom header
        val producerRecord = ProducerRecord<String, String>(
            dltTopicPartition.topic(),
            dltTopicPartition.partition(),
            record.key() as? String,
            record.value() as? String
        )
        
        // Copy existing headers from original record
        record.headers().forEach { header ->
            producerRecord.headers().add(header)
        }
        
        // Add custom x-error-category header
        producerRecord.headers().add(RecordHeader("x-error-category", errorCategory.toByteArray()))
        
        // Send to DLT and wait for completion
        kafkaTemplate.send(producerRecord).get()
    }
}

