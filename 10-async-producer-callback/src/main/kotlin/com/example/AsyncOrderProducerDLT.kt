package com.example

import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.common.errors.RetriableException
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.errors.InvalidTopicException
import org.apache.kafka.common.errors.AuthorizationException
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.NewTopic
import kotlin.random.Random
import java.util.Properties
import java.util.concurrent.TimeUnit
import java.util.concurrent.CountDownLatch

fun main(){
    val bootstrapServers = "localhost:19092"
    val topic = "orders"
    val dltTopic = "orders.dlt"
    
    val props = Properties().apply{
        put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
        put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
        put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
        put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true")
        put(ProducerConfig.ACKS_CONFIG, "all")
        put(ProducerConfig.RETRIES_CONFIG, Int.MAX_VALUE.toString())
        put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "5")
    }

    val n = 20
    val latch = CountDownLatch(n)
    val producer = KafkaProducer<String,String>(props)
    repeat(n){i->
        val randomNumber = Random.nextInt(1, 4)
        val user = "user-${(i % 5) + 1}" // user-1..user-5
        val order="""{"orderId":"o-$i","user":"${user}","amount":${randomNumber}}"""
        val record = ProducerRecord(topic, user, order)

        sendWithAppRetryInternal(producer, record, 3, latch, 1, 100, dltTopic)
    }

    val completed = latch.await(30, TimeUnit.SECONDS)
    if (!completed) System.err.println("WARN: Latch did not reach zero within timeout. Remaining=${latch.count}")
    producer.flush()
    producer.close()
}


private fun sendWithAppRetryInternal(
    producer: KafkaProducer<String,String>, 
    record: ProducerRecord<String, String>, 
    maxAttempts: Int, 
    latch: CountDownLatch,
    attempt: Int, 
    delay: Long,
    dltTopic: String
) {
    producer.send(record) { metadata, ex ->
        try {
            if (ex == null) {
                println("sent topic=${metadata.topic()} partition=${metadata.partition()} offset=${metadata.offset()} timestamp=${metadata.timestamp()}")
            } else {
                val isRetriable = ex is RetriableException
                val isFatal = ex is SerializationException || ex is InvalidTopicException || ex is AuthorizationException
                
                if (isRetriable && attempt < maxAttempts) {
                    println("RETRY attempt $attempt/$maxAttempts for ${ex::class.java.simpleName}: ${ex.message}")
                    Thread.sleep(delay)
                    val nextDelay = (delay * 2).coerceAtMost(3000L)
                    sendWithAppRetryInternal(producer, record, maxAttempts, latch, attempt + 1, nextDelay, dltTopic)
                    return@send
                } else if (isFatal) {
                    // Fatal error - send to DLT
                    System.err.println("FATAL: ${ex::class.java.simpleName}: ${ex.message}")
                    sendToDLT(producer, record, ex, dltTopic)
                } else {
                    // Non-retriable error or max attempts reached
                    System.err.println("FAIL: ${ex::class.java.simpleName}: ${ex.message}")
                }
            }
        } finally {
            latch.countDown()
        }
    }
}

private fun sendToDLT(
    producer: KafkaProducer<String, String>,
    originalRecord: ProducerRecord<String, String>,
    error: Exception,
    dltTopic: String
) {
    val dltRecord = ProducerRecord(dltTopic, originalRecord.key(), originalRecord.value()).apply {
        headers().add(RecordHeader("errorType", error::class.java.name.toByteArray()))
        headers().add(RecordHeader("errorMessage", (error.message ?: "").toByteArray()))
        headers().add(RecordHeader("originalTopic", "orders".toByteArray()))
    }
    
    producer.send(dltRecord) { metadata, ex ->
        if (ex != null) {
            System.err.println("Failed to send to DLT: ${ex.message}")
        } else {
            println("sent to DLT topic=${metadata.topic()} partition=${metadata.partition()} offset=${metadata.offset()}")
        }
    }
}