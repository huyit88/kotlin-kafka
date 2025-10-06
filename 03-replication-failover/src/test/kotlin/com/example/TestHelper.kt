package com.example

import org.testcontainers.containers.DockerComposeContainer
import java.io.File
import java.time.Duration
import java.util.*

import org.apache.kafka.clients.admin.*
import org.apache.kafka.clients.producer.*
import org.apache.kafka.clients.consumer.*
import org.apache.kafka.common.serialization.*


object TestHelper{
    fun createCluster(): DockerComposeContainer<*>{
        val composeFile = File("../docker/3-nodes/docker-compose.yml").canonicalFile
        return  DockerComposeContainer(composeFile)
            .withExposedService("kafka1", 19092)
            .withExposedService("kafka2", 29092)
            .withExposedService("kafka3", 39092)
            .withExposedService("zookeeper", 2181)
    }

    fun bootstrapServers(env: DockerComposeContainer<*>): String = listOf(
        "localhost:${env.getServicePort("kafka1", 19092)}",
        "localhost:${env.getServicePort("kafka2", 29092)}",
        "localhost:${env.getServicePort("kafka3", 39092)}"
    ).joinToString(",")

    fun ensureTopic(bootstrap: String, topic: String, partitions: Int, rf: Short) {
        val props = mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrap)
        AdminClient.create(props).use { admin ->
            val names = admin.listTopics().names().get()
            if (topic !in names) {
                admin.createTopics(listOf(NewTopic(topic, partitions, rf))).all().get()
            }
            // block until visible
            admin.describeTopics(listOf(topic)).all().get()
        }
    }

    fun producer(bootstrap: String) = KafkaProducer<String, String>(Properties().apply {
        put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap)
        put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
        put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
        put(ProducerConfig.ACKS_CONFIG, "all")
        put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true")
    })

    fun consumer(bootstrap: String, groupId: String) = KafkaConsumer<String, String>(Properties().apply {
        put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap)
        put(ConsumerConfig.GROUP_ID_CONFIG, groupId)
        put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
        put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
        put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
        put(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG,
            "org.apache.kafka.clients.consumer.CooperativeStickyAssignor")
        put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true")
    })
}