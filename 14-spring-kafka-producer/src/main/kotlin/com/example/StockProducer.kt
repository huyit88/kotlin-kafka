package com.example

import org.springframework.stereotype.Service
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.KafkaOperations
import org.springframework.kafka.support.SendResult
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.errors.RetriableException
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.errors.InvalidTopicException
import org.apache.kafka.common.errors.AuthorizationException


@Service
class StockProducer(
    private val template: KafkaTemplate<String, String>
){
    private val objectMapper = jacksonObjectMapper()
    private val topic = "inventory.events"

    fun send(event: StockChanged){
        val key = event.sku
        val eventId = event.eventId
        val value = objectMapper.writeValueAsString(event)
        val record = ProducerRecord(topic, key, value).apply{
            headers().apply {
                add(RecordHeader("eventType", "StockChanged".toByteArray()))
                add(RecordHeader("eventId", event.eventId.toByteArray()))
                add(RecordHeader("source", "StockService".toByteArray()))
            }
        }
        val future = template.executeInTransaction { ops: KafkaOperations<String, String> ->
            ops.send(record)
        }
        future.whenComplete { result: SendResult<String, String>?, ex: Throwable? ->
            if (ex != null){
                val isFatal = isFatalException(ex)
                if (isFatal) {
                    sendToDLT(key, value, eventId, ex)
                } else {
                    println("Retriable error (will be retried): ${ex.javaClass.simpleName}: ${ex.message}")
                }
            }
            if (result != null) logSuccess(key, result.recordMetadata)
        }
    }

    private fun isFatalException(ex: Throwable): Boolean {
        return ex is SerializationException || 
               ex is InvalidTopicException || 
               ex is AuthorizationException ||
               (ex !is RetriableException && ex.cause !is RetriableException)
    }

    private fun sendToDLT(key: String, value: String, eventId: String, error: Throwable) {
        val dltRecord = ProducerRecord("inventory.events.dlt", key, value).apply {
            headers().apply {
                add(RecordHeader("errorType", error.javaClass.name.toByteArray()))
                add(RecordHeader("errorMessage", (error.message ?: "").toByteArray()))
                add(RecordHeader("originalTopic", topic.toByteArray()))
                add(RecordHeader("eventId", eventId.toByteArray()))
                add(RecordHeader("sku", key.toByteArray()))
            }
        }
        val dltFuture = template.send(dltRecord)
        println("DLT published for key=$key cause=${error.javaClass.simpleName}: ${error.message}")
        dltFuture.whenComplete { resultDlt: SendResult<String, String>?, err: Throwable? ->
            if(err != null){
                println("DLT failed: ${err.message}")
            } else if(resultDlt != null) {
                println("DLT sent successfully: topic=${resultDlt.recordMetadata.topic()} partition=${resultDlt.recordMetadata.partition()}")
            }
        }
    }

    private fun logSuccess(key: String, md: RecordMetadata?) {
        if (md != null)
            println("StockChanged sent: key=$key -> ${md.topic()} p=${md.partition()} off=${md.offset()}")
    }
}