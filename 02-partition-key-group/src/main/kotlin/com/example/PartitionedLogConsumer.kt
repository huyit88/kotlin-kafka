package com.example

import java.util.Properties
import java.time.Duration

import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringDeserializer

class PartitionedLogConsumer(
    val bootstrapServers: String
){
    fun addConsumer(topic: String, group: String): KafkaConsumer<String,String>{
        val consumeProps = Properties().apply{
            put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
            put(ConsumerConfig.GROUP_ID_CONFIG, group)
            put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
            put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
            put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true")
            // smoother rebalances (optional)
            put(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG,
                "org.apache.kafka.clients.consumer.CooperativeStickyAssignor")
        }

        val consumer = KafkaConsumer<String,String>(consumeProps)
        consumer.subscribe(listOf(topic))
        return consumer
    }

    fun waitForStableAssignments(
        c1: KafkaConsumer<String,String>,
        c2: KafkaConsumer<String,String>,
        topic: String,
        expectedPartitions: Int,
        timeoutMs: Long = 5000
    ): Pair<Set<TopicPartition>, Set<TopicPartition>> {
        val deadline = System.currentTimeMillis() + timeoutMs
        var a1: Set<TopicPartition>
        var a2: Set<TopicPartition>
        do {
            c1.poll(Duration.ofMillis(200))
            c2.poll(Duration.ofMillis(200))
            a1 = c1.assignment().filter { it.topic() == topic }.toSet()
            a2 = c2.assignment().filter { it.topic() == topic }.toSet()
            // stop when total assigned equals the topic partition count
            if ((a1.size + a2.size) == expectedPartitions && (a1.isNotEmpty() && a2.isNotEmpty())) break
        } while (System.currentTimeMillis() < deadline)
        return a1 to a2
    }
}