package com.example

import kotlin.test.Test
import kotlin.test.assertTrue
import com.example.testutils.KafkaUtil
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import java.time.Duration

class AtLeastOnceDeliveryTest{
    @Test
    fun `might reprocess few messages`(){
        val topic = "logs-atleast"
        val kafka = KafkaUtil.createKafkaContainer()
        kafka.start()

        try{
            KafkaUtil.createTopicWithPartition(KafkaUtil.getAdminProps(kafka.bootstrapServers), topic, 1)

            val producerProp = KafkaUtil.getProducerProps(kafka.bootstrapServers)
            KafkaProducer<String, String>(producerProp).use{p->
                repeat(10) { i ->
                    p.send(ProducerRecord(topic, "app $i", "log-$i")).get()
                }
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
                val records = c.poll(Duration.ofMillis(1000)) // ensure we actually fetch
                var processed = 0
                for (r in records) {
                    // process record r
                    phase1 += r.partition() to r.offset()
                    if (processed == 0) {
                        // Commit the first processed record
                        c.commitSync(
                            mapOf(TopicPartition(r.topic(), r.partition()) to OffsetAndMetadata(r.offset() + 1))
                        )
                    } else if (processed == 1) {
                        // Simulate crash immediately after processing second record (no commit)
                        break
                    }
                    processed++
                }
                    
            }

            val phase2 = mutableSetOf<Pair<Int, Long>>()
            KafkaConsumer<String, String>(consumerProps).use { c ->
                val tp = TopicPartition(topic, 0)
                c.assign(listOf(tp)) // to avoid group coordination.
                // trigger initial position resolution to committed offset
                c.poll(Duration.ofMillis(0))
                val start = System.currentTimeMillis()
                while (System.currentTimeMillis() - start < 10000) {
                    val recs = c.poll(Duration.ofMillis(300))
                    recs.forEach { r -> phase2 += r.partition() to r.offset() }
                    val dupNow = phase1.intersect(phase2)
                    if (dupNow.isNotEmpty()) break
                }
            }

            // Observe behavior
            val duplicates = phase1.intersect(phase2)
            println("phase1=${phase1.size} phase2=${phase2.size} duplicates=${duplicates.size} entries=$duplicates")
            assertTrue(duplicates.isNotEmpty())
        }finally{
            kafka.stop()
        }
    }
}