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
class DurableProducerTest{
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
    fun `All 20 messages acknowledged successfully`() {
        val topic = "logs-repl"
        val bs = TestHelper.bootstrapServers(env)

        TestHelper.ensureTopic(bs, topic, partitions = 3, rf = 2)

        var total = 0
        TestHelper.producer(bs).use { p ->
            repeat(20) { i ->
                p.send(ProducerRecord(topic, "app", "log-$i")).get().also{
                    println("partition: ${it.partition()} - offset: ${it.offset()}")
                    total++
                }
            }
        }

        Assertions.assertEquals(20, total, "Should send all 20 messages")
    }
}