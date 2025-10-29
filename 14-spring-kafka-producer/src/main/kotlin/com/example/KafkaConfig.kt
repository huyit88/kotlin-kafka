package com.example

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Bean
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.*
import org.springframework.kafka.config.TopicBuilder
import org.apache.kafka.clients.admin.NewTopic


@Configuration
class KafkaConfig{
    @Bean
    fun inventoryInit(): org.apache.kafka.clients.admin.NewTopic =
        TopicBuilder.name("inventory.init").partitions(1).replicas(1).build()

    @Bean
    fun inventoryEvents(): org.apache.kafka.clients.admin.NewTopic =
        TopicBuilder.name("inventory.events").partitions(3).replicas(1).build()
}