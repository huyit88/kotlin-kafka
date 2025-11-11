package com.example

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Bean
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries
import org.apache.kafka.common.errors.SerializationException
import org.springframework.kafka.core.*
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.apache.kafka.clients.producer.ProducerConfig
import org.springframework.beans.factory.annotation.Value
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.kafka.support.serializer.JsonSerializer
import java.util.*


@Configuration
class KafkaConfig{
    @Bean
    fun listenerFactory(factory: ConcurrentKafkaListenerContainerFactory<String, Any>): ConcurrentKafkaListenerContainerFactory<String, Any> {
        val backoff = ExponentialBackOffWithMaxRetries(3).apply{
            initialInterval = 200L //200ms
            multiplier = 2.0 // 200, 400, 800
            maxInterval = 2000L
        }
        val errorHandler = DefaultErrorHandler(backoff).apply{
            addNotRetryableExceptions(
                java.lang.IllegalArgumentException::class.java,
                SerializationException::class.java
            )
        }
        factory.setCommonErrorHandler(errorHandler)
        return factory
    }

    @Bean
    fun producerFactory(@Value("\${spring.kafka.bootstrap-servers}") bootstrapServers: String): ProducerFactory<String, Any> {
        val props: MutableMap<String, Any> = HashMap()
        props[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        props[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        props[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = JsonSerializer::class.java
        props[ProducerConfig.ACKS_CONFIG] = "all"
        props[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = true
        props[ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION] = 5
        props[ProducerConfig.LINGER_MS_CONFIG] = 20
        props[ProducerConfig.BATCH_SIZE_CONFIG] = 65536
        props[ProducerConfig.COMPRESSION_TYPE_CONFIG] = "lz4"
        // Enable type headers for JsonSerializer - JsonSerializer uses this to add __TypeId__ header
        props["spring.json.add.type.headers"] = "true"
        val factory = DefaultKafkaProducerFactory<String, Any>(props)
        return factory
    }

    @Bean
    fun kafkaTemplate(producerFactory: ProducerFactory<String, Any>): KafkaTemplate<String, Any> {
        return KafkaTemplate(producerFactory)
    }
}