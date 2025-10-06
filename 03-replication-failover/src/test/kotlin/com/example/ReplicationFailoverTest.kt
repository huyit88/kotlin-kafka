package com.example

import kotlin.test.Test

import org.testcontainers.containers.DockerComposeContainer
import java.io.File
import java.time.Duration
import java.util.*

import org.apache.kafka.clients.admin.*
import org.apache.kafka.clients.producer.*
import org.apache.kafka.clients.consumer.*
import org.apache.kafka.common.serialization.*

import org.junit.jupiter.api.*
import org.testcontainers.DockerClientFactory


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReplicationFailoverTest{
    private lateinit var env: DockerComposeContainer<*>

    @BeforeAll
    fun startEnv() {
        env = TestHelper.createCluster()
        env.start()
    }

    @AfterAll
    fun stopEnv() {
        env.stop()
    }

    @Test
    fun `replication failover works with RF=2`() {
        val topic = "logs-repl"
        val bs = TestHelper.bootstrapServers(env)
        val props = mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to bs)

        TestHelper.ensureTopic(bs, topic, partitions = 3, rf = 2)

        TestHelper.consumer(bs, "warmup-${System.nanoTime()}").use { c ->
            c.subscribe(listOf(topic))
            c.poll(Duration.ofMillis(1000))
        }

        AdminClient.create(props).use { admin ->
            val desc: Map<String,TopicDescription> = admin.describeTopics(listOf(topic)).all().get()
            val partitions = desc.get(topic)!!.partitions()
            partitions.forEachIndexed { idx, p ->
                val leaderId = p.leader().id()
                val replicaIds = p.replicas().map { it.id() }
                println("before turnoff: partition=$idx leader=$leaderId replicas=$replicaIds")
            }
        }

        TestHelper.producer(bs).use { p ->
            repeat(20) { i ->
                p.send(ProducerRecord(topic, "app", "log-$i")).get()
            }
        }

        //Stop one broker to simulate failure 
        val state = env.getContainerByServiceName("kafka1_1").orElseThrow()
        println("Stopping containerId=${state.containerId}")
        val docker = DockerClientFactory.instance().client()
        docker.stopContainerCmd(state.containerId).exec()


        //Consume from the cluster via the multi-bootstrap string
        val group = "replica-test-${System.nanoTime()}"
        val c = TestHelper.consumer(bs, group)
        c.subscribe(listOf("logs-repl"))

        var total = 0
        repeat(20) {
            total += c.poll(Duration.ofMillis(500)).count()
            if (total >= 20) return@repeat
        }
        c.close()
                
        AdminClient.create(props).use { admin ->
            val desc: Map<String,TopicDescription> = admin.describeTopics(listOf(topic)).all().get()
            val partitions = desc.get(topic)!!.partitions()
            partitions.forEachIndexed { idx, p ->
                val leaderId = p.leader().id()
                val replicaIds = p.replicas().map { it.id() }
                println("after turnoff: partition=$idx leader=$leaderId replicas=$replicaIds")
            }
            Assertions.assertEquals(3, partitions.size)
            Assertions.assertTrue(partitions.all{ it.isr().size >= 1 && it.replicas().size == 2})
            // replicas include both leader and follower
        }
        Assertions.assertEquals(20, total, "Should read all 20 messages after broker failover")
    }
}