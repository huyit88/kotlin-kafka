package com.example

import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StoreQueryParameters
import org.apache.kafka.streams.state.QueryableStoreTypes
import org.springframework.kafka.config.StreamsBuilderFactoryBean
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class UserStatusController(
    private val streamsBuilderFactoryBean: StreamsBuilderFactoryBean,
    private val kafkaTemplate: KafkaTemplate<String, String>
) {
    companion object {
        private const val USER_PRESENCE_TOPIC = "user-presence"
    }

    @GetMapping("/status/{userId}")
    fun status(@PathVariable userId: String): Map<String, String?> {
        val kafkaStreams: KafkaStreams = streamsBuilderFactoryBean.kafkaStreams
            ?: throw IllegalStateException("KafkaStreams is not yet initialized")
        
        val store = kafkaStreams.store(
            StoreQueryParameters.fromNameAndType(
                "user-status-store",
                QueryableStoreTypes.keyValueStore<String, String>()
            )
        )

        val status: String? = store.get(userId) ?: "unknown"

        return mapOf(
            "userId" to userId,
            "status" to status
        )
    }

    @DeleteMapping("/status/{userId}")
    fun deleteStatus(@PathVariable userId: String): Map<String, String> {
        kafkaTemplate.send(USER_PRESENCE_TOPIC, userId, null)
        
        return mapOf(
            "message" to "Tombstone sent for user: $userId",
            "userId" to userId
        )
    }
}