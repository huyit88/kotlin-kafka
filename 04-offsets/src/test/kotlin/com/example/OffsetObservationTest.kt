package com.example

import kotlin.test.Test
import kotlin.test.assertEquals
import com.example.testutils.KafkaUtil
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import java.time.Duration

class OffsetObservationTest{
    @Test
    fun `offsets increase per partition`(){
        val topic = "logs-offset"
        val kafka = KafkaUtil.createKafkaContainer()
        kafka.start()

        try{
            KafkaUtil.createTopicWithPartition(KafkaUtil.getAdminProps(kafka.bootstrapServers), topic, 3)

            val producerProp = KafkaUtil.getProducerProps(kafka.bootstrapServers)
            KafkaProducer<String, String>(producerProp).use{p->
                repeat(10) { i ->
                    p.send(ProducerRecord(topic, "app $i", "log-$i")).get()
                }
            }

            var total = 0
            val groupId = "group" + System.nanoTime()
            val consumerProps = KafkaUtil.getConsumerProps(kafka.bootstrapServers, groupId)
            KafkaConsumer<String, String>(consumerProps).use{c->
                c.subscribe(listOf(topic))
                val lastOffsetByPartition = mutableMapOf<Int, Long>()   
                repeat(10) {
                    val records = c.poll(Duration.ofMillis(500))
                    records.forEach { r ->
                        println("partition=${r.partition()} offset=${r.offset()} key=${r.key()} value=${r.value()}")  
                        val last = lastOffsetByPartition[r.partition()]
                        if (last != null) {
                            kotlin.test.assertTrue(r.offset() > last, "offset must increase within partition ${r.partition()}")
                        }
                        lastOffsetByPartition[r.partition()] = r.offset()                  
                    }
                    total += records.count()
                    if (total >= 10) return@repeat
                }
            }

            assertEquals(10, total, "should read all messages")
        }finally{
            kafka.stop()
        }
    }
}