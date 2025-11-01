package com.example


import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Bean
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.*
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries
import org.springframework.kafka.listener.ContainerProperties
import org.apache.kafka.clients.consumer.ConsumerConfig

@Configuration
class BatchListenerConfig {

    @Bean(name = ["batchFactory"])
    fun batchFactory(
        consumerFactory: ConsumerFactory<String, String>,
        @Value("\${spring.kafka.listener.concurrency:3}") concurrency: Int,
        @Value("\${spring.kafka.listener.ack-mode:MANUAL_IMMEDIATE}") ackModeProp: String
    ): ConcurrentKafkaListenerContainerFactory<String, String> {

        val cf = ConcurrentKafkaListenerContainerFactory<String, String>()
        cf.consumerFactory = consumerFactory

        // Batch mode ON
        cf.isBatchListener = true

        // Concurrency (needs >= topic partitions to be effective)
        cf.setConcurrency(concurrency)

        // Ack mode (default MANUAL_IMMEDIATE for explicit ack per batch)
        cf.containerProperties.ackMode = when (ackModeProp.uppercase()) {
            "RECORD" -> ContainerProperties.AckMode.RECORD
            "BATCH" -> ContainerProperties.AckMode.BATCH
            "TIME" -> ContainerProperties.AckMode.TIME
            "COUNT" -> ContainerProperties.AckMode.COUNT
            "COUNT_TIME" -> ContainerProperties.AckMode.COUNT_TIME
            "MANUAL" -> ContainerProperties.AckMode.MANUAL
            else -> ContainerProperties.AckMode.MANUAL_IMMEDIATE // default
        }

        // Simple retry/backoff for listener exceptions
        cf.setCommonErrorHandler(
            DefaultErrorHandler(
                ExponentialBackOffWithMaxRetries(3).apply {
                    initialInterval = 200
                    multiplier = 2.0
                    maxInterval = 2000
                }
            )
        )

        // (optional) poll timeout
        cf.containerProperties.pollTimeout = 1500

        return cf
    }
}