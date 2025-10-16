package com.example

import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsOptions
import org.apache.kafka.common.TopicPartition
import java.util.Properties

fun main(){
    val props = Properties().apply {
        put("bootstrap.servers", "localhost:19092")
    }

    AdminClient.create(props).use { admin ->
        val group = "team-metrics"
        val offsets = admin.listConsumerGroupOffsets(group)
            .partitionsToOffsetAndMetadata()
            .get()

        val endOffsets = admin.listOffsets(
            offsets.keys.associateWith { _ -> org.apache.kafka.clients.admin.OffsetSpec.latest() }
        ).all().get()

        println("GROUP | TOPIC | PARTITION | CURRENT | END | LAG")
        offsets.forEach { (tp, meta) ->
            val current = meta?.offset() ?: -1
            val end = endOffsets[tp]?.offset() ?: -1
            println("$group | ${tp.topic()} | ${tp.partition()} | $current | $end | ${end - current}")
        }
    }
}