package com.example


import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic

import com.example.testutils.KafkaUtil
import java.util.Properties

import kotlin.test.Test
import kotlin.test.assertEquals

class PartitionedLogProducerTest{    
    @Test
    fun `should use same partition for same key`(){
        val kafka = KafkaUtil.createKafkaContainer()
        kafka.start()

        try{
            val adminProps = KafkaUtil.getAdminProps(kafka.bootstrapServers)
            KafkaUtil.createTopicWithPartition(adminProps, "logs", 3)
            val producer = PartitionedLogProducer(kafka.bootstrapServers)
            val appAPartitions = mutableSetOf<Int>()
            val appBPartitions = mutableSetOf<Int>()
            val appCPartitions = mutableSetOf<Int>()

            repeat(10){
                val resA = producer.send("app-a", "message app-a $it")
                val resB = producer.send("app-b", "message app-b $it")
                val resC = producer.send("app-c", "message app-c $it")
                println("message app-a $it: " + resA.partition())
                appAPartitions.add(resA.partition())
                println("message app-b $it: " + resB.partition())
                appBPartitions.add(resB.partition())
                println("message app-c $it: " + resC.partition())
                appCPartitions.add(resC.partition())
            }

            assertEquals(1, appAPartitions.size)
            assertEquals(1, appBPartitions.size)
            assertEquals(1, appCPartitions.size)
            producer.close()
        }finally{
            kafka.stop()
        }
    }
}