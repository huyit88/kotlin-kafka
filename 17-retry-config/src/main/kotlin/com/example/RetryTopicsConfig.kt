package com.example

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.apache.kafka.common.errors.SerializationException
import org.springframework.kafka.retrytopic.RetryTopicConfigurationBuilder
import org.springframework.kafka.retrytopic.RetryTopicConfiguration
import org.springframework.kafka.listener.ListenerExecutionFailedException
import org.springframework.kafka.core.KafkaTemplate
@Configuration
class RetryTopicsConfig(
    val template: KafkaTemplate<String,Any>
){
    @Bean
    fun retryTopicConfiguration(): RetryTopicConfiguration{
        return RetryTopicConfigurationBuilder.newInstance()
            .exponentialBackoff(
                500,
                2.0,
                10_000
            )
            .maxAttempts(4) // 1 main and 3 retries
            .notRetryOn(
                listOf(
                    java.lang.IllegalArgumentException::class.java,
                    SerializationException::class.java
                )
            )
            .create(template)
    }
}