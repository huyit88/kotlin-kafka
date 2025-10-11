package com.example

import kotlin.test.Test
import kotlin.test.assertEquals
import com.example.testutils.KafkaUtil
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.TopicPartition
import java.time.Duration

class IdempotentProducerTest{
    @Test
    fun `producer should retry and cause no duplicate messages`(){
        val topic = "logs-idempotent"
        val kafka = KafkaUtil.createKafkaContainer()
        kafka.start()

        try{
            KafkaUtil.createTopicWithPartition(KafkaUtil.getAdminProps(kafka.bootstrapServers), topic, 1)

            val props = KafkaUtil.getProducerProps(kafka.bootstrapServers).apply {
            put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true")
            put(ProducerConfig.ACKS_CONFIG, "all")
            put(ProducerConfig.RETRIES_CONFIG, Int.MAX_VALUE)
            put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 200)          // fast timeout
            put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120_000)
            put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5)
        }
        KafkaProducer<String, String>(props).use { p ->
            repeat(10) { i ->
                if (i == 3) KafkaUtil.pause(kafka)          // briefly cut the broker
                p.send(ProducerRecord(topic, "app $i", "log-$i"))
                if (i == 3) {
                    Thread.sleep(1000)
                    KafkaUtil.unpause(kafka)
                }
            }
            p.flush()
        }

            val groupId = "group-" + System.nanoTime()
            val consumerProps = KafkaUtil.getConsumerProps(kafka.bootstrapServers, groupId).apply{
                put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
            }

            val phase1 = mutableSetOf<Pair<Int, Long>>()
            KafkaConsumer<String, String>(consumerProps).use { c ->
                c.subscribe(listOf(topic))
                while (c.assignment().isEmpty()) {
                    c.poll(Duration.ofMillis(50))
                }
                c.seekToBeginning(c.assignment())
                val start = System.currentTimeMillis()
                while(System.currentTimeMillis() - start < 5000 && phase1.size < 10){
                    val records = c.poll(Duration.ofMillis(250)) // accumulate until we have all
                    for (r in records) {
                        phase1 += r.partition() to r.offset()
                        c.commitSync(
                            mapOf(TopicPartition(r.topic(), r.partition()) to OffsetAndMetadata(r.offset() + 1))
                        )
                    }
                }
            }

            // Observe behavior
            println("phase1=${phase1.size}")
            val offsets = phase1.filter { it.first == 0 }.map { it.second }.sorted()
            assertEquals(10, offsets.size)
            assertEquals((0L..9L).toList(), offsets)
        }finally{
            kafka.stop()
        }
    }
}