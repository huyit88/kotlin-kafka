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
import java.util.HashMap

@Configuration
class KafkaConfig{
    @Bean
    fun listenerFactory(factory: ConcurrentKafkaListenerContainerFactory<String, String>):
        ConcurrentKafkaListenerContainerFactory<String, String> {
        factory.setCommonErrorHandler(
            DefaultErrorHandler(
                ExponentialBackOffWithMaxRetries(3).apply {
                    initialInterval = 200
                    multiplier = 2.0
                    maxInterval = 2000
                }
            )
        )
        factory.containerProperties.setConsumerRebalanceListener(MyRebalanceListener())
        return factory
    }
}