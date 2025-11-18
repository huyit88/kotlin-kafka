package com.example

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.Properties

@Service
class DltProbeService {
    
    fun getLatestDltRecord(): Map<String, Any> {
        val props = Properties().apply {
            put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:19092")
            put(ConsumerConfig.GROUP_ID_CONFIG, "dlt-probe-group-${System.currentTimeMillis()}")
            put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
            put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
            put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
        }
        
        val consumer = KafkaConsumer<String, String>(props)
        
        try {
            consumer.subscribe(listOf("my-topic.DLT"))
            
            // Wait for partition assignment (may need multiple polls)
            var assignmentAttempts = 0
            var partitions = consumer.assignment()
            while (partitions.isEmpty() && assignmentAttempts < 20) {
                consumer.poll(Duration.ofMillis(200))
                partitions = consumer.assignment()
                assignmentAttempts++
            }
            
            // Get assigned partitions
            if (partitions.isEmpty()) {
                return emptyMap()
            }
            
            // Get end offsets to check if topic has records
            val endOffsets = try {
                consumer.endOffsets(partitions)
            } catch (e: Exception) {
                // Topic might not exist or other error
                return emptyMap()
            }
            val hasRecords = endOffsets.values.any { it > 0 }
            if (!hasRecords) {
                return emptyMap()
            }
            
            // Seek to beginning and poll until we've read all records
            consumer.seekToBeginning(partitions)
            
            var latestRecord: org.apache.kafka.clients.consumer.ConsumerRecord<String, String>? = null
            var emptyPolls = 0
            val maxEmptyPolls = 3
            var totalRecordsRead = 0
            
            // Poll until we've read all records
            while (emptyPolls < maxEmptyPolls) {
                val records = consumer.poll(Duration.ofMillis(1000))
                
                if (records.isEmpty) {
                    emptyPolls++
                } else {
                    emptyPolls = 0
                    totalRecordsRead += records.count()
                    // Update latest record
                    records.forEach { record ->
                        if (latestRecord == null) {
                            latestRecord = record
                        } else {
                            // Compare by offset (higher is newer)
                            // If same offset, prefer the one with later timestamp
                            if (record.offset() > latestRecord!!.offset() || 
                                (record.offset() == latestRecord!!.offset() && record.timestamp() > latestRecord!!.timestamp())) {
                                latestRecord = record
                            }
                        }
                    }
                }
            }
            
            if (latestRecord == null) {
                return emptyMap()
            }
            
            // Extract all DLT headers
            val headers = mutableMapOf<String, String>()
            
            latestRecord!!.headers().forEach { header ->
                val headerName = header.key()
                val headerValue = String(header.value())
                headers[headerName] = headerValue
            }
            
            // Return full record information
            return mapOf(
                "topic" to latestRecord!!.topic(),
                "partition" to latestRecord!!.partition().toString(),
                "offset" to latestRecord!!.offset().toString(),
                "key" to (latestRecord!!.key() ?: ""),
                "value" to (latestRecord!!.value() ?: ""),
                "headers" to headers
            )
        } finally {
            consumer.close()
        }
    }
    
    fun getLatestDltRecordHeaders(): Map<String, String> {
        val record = getLatestDltRecord()
        return (record["headers"] as? Map<String, String>) ?: emptyMap()
    }
}