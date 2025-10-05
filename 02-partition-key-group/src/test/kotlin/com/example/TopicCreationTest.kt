package com.example


import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic

import com.example.testutils.KafkaUtil
import java.util.Properties

import kotlin.test.Test
import kotlin.test.assertEquals

class TopicCreationTest{
    @Test
    fun `should create topic with 3 partitions`(){
        val kafka = KafkaUtil.createKafkaContainer()
        kafka.start()

        try{
            val adminProps = KafkaUtil.getAdminProps(kafka.bootstrapServers)

            KafkaUtil.createTopicWithPartition(adminProps, "logs", 3)

            AdminClient.create(adminProps).use{admin->
                val desc = admin.describeTopics(listOf("logs")).values().get("logs")!!.get()
                assertEquals(3, desc.partitions().size)
            }
        }finally{
            kafka.stop()
        }
    }
}