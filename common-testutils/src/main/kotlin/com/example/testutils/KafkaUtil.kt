package com.example.testutils

import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.utility.DockerImageName
import org.testcontainers.DockerClientFactory
import java.time.Duration
import java.util.Properties
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.common.serialization.StringDeserializer
object KafkaUtil {
    fun createKafkaContainer(): KafkaContainer {
        return KafkaContainer(
            DockerImageName.parse("apache/kafka:3.7.0")
            )
            .withTmpFs(
                mapOf(
                    "/opt/kafka/logs" to "rw,size=256m",
                    "/var/lib/kafka/data" to "rw,size=512m",
                    "/tmp/kafka-logs" to "rw,size=512m"
                )
            )
            .withEnv(
                "KAFKA_JVM_PERFORMANCE_OPTS",
                "-server -XX:+UseG1GC -Xms256m -Xmx512m -XX:MaxGCPauseMillis=20 -XX:InitiatingHeapOccupancyPercent=35 -XX:+ExplicitGCInvokesConcurrent -Xlog:gc*:stdout:time,tags"
            )
            .withStartupTimeout(Duration.ofSeconds(120))
    }

    fun pause(container: KafkaContainer){
        val dockerClient = DockerClientFactory.instance().client()
        dockerClient.pauseContainerCmd(container.containerId).exec()
    }

    fun unpause(container: KafkaContainer){
        val dockerClient = DockerClientFactory.instance().client()
        dockerClient.unpauseContainerCmd(container.containerId).exec()
    }

    fun createTopicWithPartition(props: Properties, topic: String, partition: Int, replication:Int = 1){        
        AdminClient.create(props).use{ admin ->
            val existing = admin.listTopics().names().get()
            if(!existing.contains(topic)){
                val newTopic = NewTopic(topic, partition, replication.toShort())
                admin.createTopics(listOf(newTopic)).all().get()
            }
        }
    }

    fun getAdminProps(bootstrapServers: String): Properties{
        return Properties().apply{
            put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)        
        }
    }

    fun getConsumerProps(bootstrapServers: String, groupId: String): Properties{
        return Properties().apply{
            put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
            put(ConsumerConfig.GROUP_ID_CONFIG, groupId)
            put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
            put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
            put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true")
        }
    }

    fun getProducerProps(bootstrapServers: String): Properties{
        return Properties().apply{
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ProducerConfig.ACKS_CONFIG, "all")
            put(ProducerConfig.RETRIES_CONFIG, 3)
            put(ProducerConfig.LINGER_MS_CONFIG, 0)
        }
    }
}


