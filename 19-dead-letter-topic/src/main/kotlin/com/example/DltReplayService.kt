package com.example

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.*

@Service
class DltReplayService(private val kafkaTemplate: KafkaTemplate<String, String>) {
    private val logger = LoggerFactory.getLogger(DltReplayService::class.java)

    fun replay(): Int {
        val props = Properties().apply {
            put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:19092")
            put(ConsumerConfig.GROUP_ID_CONFIG, "dlt-replay-${System.currentTimeMillis()}")
            put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
            put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
            put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false)
        }
        
        val dltTopic = "payments.DLT"
        var replayCount = 0
        
        KafkaConsumer<String, String>(props).use { consumer ->
            consumer.subscribe(listOf(dltTopic))
            
            // Wait for partition assignment
            consumer.poll(Duration.ofMillis(100))
            
            val partitions = consumer.assignment()
            if (partitions.isEmpty()) {
                logger.warn("No partitions assigned for DLT topic")
                return 0
            }
            
            // Seek to beginning to consume all records
            consumer.seekToBeginning(partitions)
            
            // Consume all records from DLT
            var hasMore = true
            while (hasMore) {
                val records = consumer.poll(Duration.ofMillis(1000))
                
                if (records.isEmpty) {
                    hasMore = false
                } else {
                    for (record in records) {
                        // Create producer record with original key/value
                        val producerRecord = ProducerRecord<String, String>(
                            "payments",
                            record.key(),
                            record.value()
                        )
                        
                        // Copy existing headers from original record
                        record.headers().forEach { header ->
                            producerRecord.headers().add(header)
                        }
                        
                        // Add x-replayed header
                        producerRecord.headers().add(RecordHeader("x-replayed", "true".toByteArray()))
                        
                        // Send to main topic
                        kafkaTemplate.send(producerRecord).get()
                        
                        // Log the replay
                        logger.debug("Replayed from DLT: ${record.value()}")
                        replayCount++
                    }
                    
                    // Commit offsets manually to avoid duplicates
                    consumer.commitSync()
                }
            }
        }
        
        return replayCount
    }
}