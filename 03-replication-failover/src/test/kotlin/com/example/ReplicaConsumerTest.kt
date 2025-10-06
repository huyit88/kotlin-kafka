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
class ReplicaConsumerTest{
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
    fun `No messages are lost or duplicated`() {
        val topic = "logs-repl"
        val bs = TestHelper.bootstrapServers(env)

        TestHelper.ensureTopic(bs, topic, partitions = 3, rf = 2)

        TestHelper.consumer(bs, "warmup-${System.nanoTime()}").use { c ->
            c.subscribe(listOf(topic))
            c.poll(Duration.ofMillis(500))
        }

        TestHelper.producer(bs).use { p ->
            repeat(20) { i ->
                p.send(ProducerRecord(topic, "app", "log-$i")).get().also{
                    println("partition: ${it.partition()} - offset: ${it.offset()}")
                }
            }
        }

        val group = "replica-test-${System.nanoTime()}"
        val c = TestHelper.consumer(bs, group)
        c.subscribe(listOf(topic))

        var total = 0
        val lastOffsets = mutableMapOf<Int, Long>()
        repeat(20) {
            val records = c.poll(Duration.ofMillis(500))
            records.forEach { r ->
                println("partition=${r.partition()} offset=${r.offset()} key=${r.key()}")
                lastOffsets[r.partition()]?.let { prev ->
                    Assertions.assertTrue(r.offset() > prev, "Offsets must increase per partition")
                }
                lastOffsets[r.partition()] = r.offset()
            }
            total += records.count()
            if (total >= 20) return@repeat
        }
        c.close()

        Assertions.assertEquals(20, total, "Should read all 20 messages")
    }
}