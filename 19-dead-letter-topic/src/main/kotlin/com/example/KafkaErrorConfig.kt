package com.example

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Bean
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.core.*
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.util.backoff.FixedBackOff
import org.apache.kafka.common.TopicPartition
import java.util.*


@Configuration
class KafkaErrorConfig{
    companion object {
        const val BOOTSTRAP_SERVERS = "localhost:19092"
    }

    @Bean
    fun consumerFactory(): ConsumerFactory<String, String> {
        val props = mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to BOOTSTRAP_SERVERS,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java
        )
        return DefaultKafkaConsumerFactory(props)
    }

    @Bean
    fun kafkaListenerContainerFactory(
        kafkaTemplate: KafkaTemplate<String, String>,
        consumerFactory: ConsumerFactory<String, String>): ConcurrentKafkaListenerContainerFactory<String, String> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
        factory.consumerFactory = consumerFactory
        val errorHandler = DefaultErrorHandler(
            dltRecovererCustom(kafkaTemplate),
            FixedBackOff(1000, 2) // 1s backoff, 2 retries
        )
        errorHandler.addNotRetryableExceptions(
            IllegalArgumentException::class.java
        )
        factory.setCommonErrorHandler(errorHandler)
        return factory
    }

    @Bean
    fun dltRecoverer(kafkaTemplate: KafkaTemplate<String, String>) = DeadLetterPublishingRecoverer(kafkaTemplate) { record, _ ->
        TopicPartition("${record.topic()}.DLT", record.partition())
    }

    @Bean
    fun dltRecovererCustom(kafkaTemplate: KafkaTemplate<String, String>) = CustomDeadLetterRecoverer(kafkaTemplate) 
    
    @Bean
    fun producerFactory(): ProducerFactory<String, String> {
        val props: MutableMap<String, Any> = HashMap()
        props[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = BOOTSTRAP_SERVERS
        props[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        props[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        val factory = DefaultKafkaProducerFactory<String, String>(props)
        return factory
    }

    @Bean
    fun kafkaTemplate(producerFactory: ProducerFactory<String, String>): KafkaTemplate<String, String> {
        return KafkaTemplate(producerFactory)
    }
}