package com.example.testutils

import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.util.Properties
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic

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
}


