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
class ReplicatedTopicCreationTest{
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
    fun `expect 1 leader and 1 follower`() {
        val topic = "logs-repl"
        val bs = TestHelper.bootstrapServers(env)

        TestHelper.ensureTopic(bs, topic, partitions = 3, rf = 2)

        TestHelper.consumer(bs, "warmup-${System.nanoTime()}").use { c ->
            c.subscribe(listOf(topic))
            c.poll(Duration.ofMillis(500))
        }

        val props = mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to bs)
        AdminClient.create(props).use { admin ->
            val desc: Map<String,TopicDescription> = admin.describeTopics(listOf(topic)).all().get()
            val partitions = desc.get(topic)!!.partitions()
            partitions.forEachIndexed { idx, p ->
                val leaderId = p.leader().id()
                val replicaIds = p.replicas().map { it.id() }
                println("partition=$idx leader=$leaderId replicas=$replicaIds")
            }
            Assertions.assertEquals(3, partitions.size)
            Assertions.assertTrue(partitions.all{ it.isr().size == 2 && it.replicas().size == 2})
            // replicas include both leader and follower
        }
    }
}