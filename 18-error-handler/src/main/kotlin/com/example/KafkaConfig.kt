package com.example

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.RetryListener
import org.springframework.util.backoff.FixedBackOff
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.common.errors.SerializationException
import org.springframework.kafka.support.KafkaHeaders

@Configuration
class KafkaConfig(
    private val registry: MeterRegistry
){
    companion object {
        const val BOOTSTRAP_SERVERS = "localhost:19092"
        const val TOPIC = "my-topic"
    }
    
    private val retriedCounter = registry.counter("kafka.errors.retried")
    private val dltCounter = registry.counter("kafka.errors.dlt")

    @Bean
    fun consumerFactory(): ConsumerFactory<String, String> {
        val props = mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to BOOTSTRAP_SERVERS,
            ConsumerConfig.GROUP_ID_CONFIG to "error-demo-group",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to ErrorHandlingDeserializer::class.java,
            ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS to StringDeserializer::class.java
        )
        return DefaultKafkaConsumerFactory(props)
    }

    @Bean
    fun kafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, String> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
        factory.consumerFactory = consumerFactory()
        factory.setCommonErrorHandler(customErrorHandler())
        return factory
    }

    @Bean
    fun customErrorHandler(): DefaultErrorHandler {
        // Use a custom recoverer that adds the x-error-category header
        val recoverer = CustomDeadLetterRecoverer(kafkaTemplate(), dltCounter)
        
        val retryListener = object : RetryListener {
            override fun failedDelivery(record: ConsumerRecord<*, *>, ex: Exception, deliveryAttempt: Int) {
                // Increment counter for each retry attempt (deliveryAttempt > 1 means it's a retry)
                if (deliveryAttempt > 1) {
                    retriedCounter.increment()
                }
            }
            
            override fun recovered(record: ConsumerRecord<*, *>, ex: Exception) {
                // Called when a record is successfully recovered after retries
            }
            
            override fun recoveryFailed(record: ConsumerRecord<*, *>, original: Exception, failure: Exception) {
                // Called when recovery fails
            }
        }
        
        val handler = DefaultErrorHandler(recoverer, FixedBackOff(2000L, 2))
        handler.setRetryListeners(retryListener)
        
        // Add not retryable exceptions for deserialization errors
        handler.addNotRetryableExceptions(
            SerializationException::class.java,
            IllegalArgumentException::class.java
        )
        
        return handler
    }

    @Bean
    fun kafkaTemplate(): KafkaTemplate<String, String> =
        KafkaTemplate(DefaultKafkaProducerFactory(mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to BOOTSTRAP_SERVERS,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java
        )))
    
    @Bean
    fun binaryKafkaTemplate(): KafkaTemplate<String, ByteArray> =
        KafkaTemplate(DefaultKafkaProducerFactory(mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to BOOTSTRAP_SERVERS,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to ByteArraySerializer::class.java
        )))
}