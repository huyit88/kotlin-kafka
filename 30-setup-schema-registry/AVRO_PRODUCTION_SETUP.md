# Avro + Schema Registry: Production Setup Guide

## Current Setup Analysis

### ✅ What's Good
- **Specific Avro classes** (not GenericRecord) - type-safe, production-ready
- **Schema generation** via Gradle plugin - automatic class generation
- **Schema Registry integration** - centralized schema management
- **Type-safe producers/consumers** - compile-time safety

### ⚠️ Current Limitations
- **Single schema only** - hardcoded to `Transaction`
- **One producer/consumer factory** - not scalable for multiple schemas
- **No shared configuration** - duplicated settings

---

## Handling Multiple Schemas

### Approach 1: Multiple Named Factories (Recommended for Different Types)

**Use when:** You have distinct schemas that need separate configuration (different topics, different consumer groups, etc.)

```kotlin
@Configuration
class KafkaConfig {
    
    // Shared base configuration
    private fun baseProducerProps(
        bootstrapServers: String,
        schemaRegistryUrl: String
    ): Map<String, Any> = mapOf(
        ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to KafkaAvroSerializer::class.java,
        "schema.registry.url" to schemaRegistryUrl,
        ProducerConfig.ACKS_CONFIG to "all",
        ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to true
    )
    
    private fun baseConsumerProps(
        bootstrapServers: String,
        schemaRegistryUrl: String,
        groupId: String
    ): Map<String, Any> = mapOf(
        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to KafkaAvroDeserializer::class.java,
        ConsumerConfig.GROUP_ID_CONFIG to groupId,
        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
        "schema.registry.url" to schemaRegistryUrl,
        "specific.avro.reader" to true
    )
    
    // Transaction Producer
    @Bean
    fun transactionProducerFactory(
        @Value("\${spring.kafka.bootstrap-servers}") bootstrapServers: String,
        @Value("\${schema.registry.url}") schemaRegistryUrl: String
    ): ProducerFactory<String, Transaction> {
        return DefaultKafkaProducerFactory(baseProducerProps(bootstrapServers, schemaRegistryUrl))
    }
    
    @Bean
    fun transactionKafkaTemplate(
        transactionProducerFactory: ProducerFactory<String, Transaction>
    ): KafkaTemplate<String, Transaction> {
        return KafkaTemplate(transactionProducerFactory)
    }
    
    // Payment Producer (different schema)
    @Bean
    fun paymentProducerFactory(
        @Value("\${spring.kafka.bootstrap-servers}") bootstrapServers: String,
        @Value("\${schema.registry.url}") schemaRegistryUrl: String
    ): ProducerFactory<String, Payment> {
        return DefaultKafkaProducerFactory(baseProducerProps(bootstrapServers, schemaRegistryUrl))
    }
    
    @Bean
    fun paymentKafkaTemplate(
        paymentProducerFactory: ProducerFactory<String, Payment>
    ): KafkaTemplate<String, Payment> {
        return KafkaTemplate(paymentProducerFactory)
    }
    
    // Transaction Consumer
    @Bean
    fun transactionConsumerFactory(
        @Value("\${spring.kafka.bootstrap-servers}") bootstrapServers: String,
        @Value("\${schema.registry.url}") schemaRegistryUrl: String
    ): ConsumerFactory<String, Transaction> {
        return DefaultKafkaConsumerFactory(
            baseConsumerProps(bootstrapServers, schemaRegistryUrl, "fraud-detector")
        )
    }
    
    @Bean
    fun transactionListenerContainerFactory(
        transactionConsumerFactory: ConsumerFactory<String, Transaction>
    ): ConcurrentKafkaListenerContainerFactory<String, Transaction> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, Transaction>()
        factory.consumerFactory = transactionConsumerFactory
        return factory
    }
    
    // Payment Consumer (different consumer group)
    @Bean
    fun paymentConsumerFactory(
        @Value("\${spring.kafka.bootstrap-servers}") bootstrapServers: String,
        @Value("\${schema.registry.url}") schemaRegistryUrl: String
    ): ConsumerFactory<String, Payment> {
        return DefaultKafkaConsumerFactory(
            baseConsumerProps(bootstrapServers, schemaRegistryUrl, "payment-processor")
        )
    }
    
    @Bean
    fun paymentListenerContainerFactory(
        paymentConsumerFactory: ConsumerFactory<String, Payment>
    ): ConcurrentKafkaListenerContainerFactory<String, Payment> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, Payment>()
        factory.consumerFactory = paymentConsumerFactory
        return factory
    }
}
```

**Usage:**
```kotlin
@RestController
class PaymentController(
    private val transactionTemplate: KafkaTemplate<String, Transaction>,
    private val paymentTemplate: KafkaTemplate<String, Payment>
) {
    @PostMapping("/tx")
    fun createTransaction(@RequestBody req: TxRequest) {
        val tx = Transaction.newBuilder()
            .setTransactionId(req.transactionId)
            .setAmount(req.amount)
            .setCurrency(req.currency)
            .build()
        transactionTemplate.send("transactions-avro", req.transactionId, tx)
    }
    
    @PostMapping("/payment")
    fun createPayment(@RequestBody req: PaymentRequest) {
        val payment = Payment.newBuilder()
            .setPaymentId(req.paymentId)
            .setAmount(req.amount)
            .build()
        paymentTemplate.send("payments-avro", req.paymentId, payment)
    }
}

@Component
class PaymentConsumer {
    @KafkaListener(
        topics = ["payments-avro"],
        containerFactory = "paymentListenerContainerFactory"
    )
    fun onPayment(record: ConsumerRecord<String, Payment>) {
        val payment = record.value()
        // Type-safe access
        println("Payment: ${payment.paymentId} - ${payment.amount}")
    }
}
```

---

### Approach 2: Generic Factory with Type Parameter (Advanced)

**Use when:** You have many schemas and want to reduce boilerplate.

```kotlin
@Configuration
class KafkaConfig {
    
    // Generic producer factory creator
    private inline fun <reified T> createAvroProducerFactory(
        bootstrapServers: String,
        schemaRegistryUrl: String
    ): ProducerFactory<String, T> {
        val props = mutableMapOf<String, Any>(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to KafkaAvroSerializer::class.java,
            "schema.registry.url" to schemaRegistryUrl
        )
        return DefaultKafkaProducerFactory<String, T>(props)
    }
    
    // Generic consumer factory creator
    private inline fun <reified T> createAvroConsumerFactory(
        bootstrapServers: String,
        schemaRegistryUrl: String,
        groupId: String
    ): ConsumerFactory<String, T> {
        val props = mutableMapOf<String, Any>(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to KafkaAvroDeserializer::class.java,
            ConsumerConfig.GROUP_ID_CONFIG to groupId,
            "schema.registry.url" to schemaRegistryUrl,
            "specific.avro.reader" to true
        )
        return DefaultKafkaConsumerFactory<String, T>(props)
    }
    
    // Specific beans using the generic creator
    @Bean
    fun transactionProducerFactory(
        @Value("\${spring.kafka.bootstrap-servers}") bootstrapServers: String,
        @Value("\${schema.registry.url}") schemaRegistryUrl: String
    ) = createAvroProducerFactory<Transaction>(bootstrapServers, schemaRegistryUrl)
    
    @Bean
    fun transactionKafkaTemplate(
        transactionProducerFactory: ProducerFactory<String, Transaction>
    ) = KafkaTemplate(transactionProducerFactory)
    
    // Add more as needed...
}
```

---

## Production Best Practices

### 1. Schema Organization

```
src/main/avro/
  com/
    example/
      transactions/
        Transaction.avsc
        TransactionV2.avsc
      payments/
        Payment.avsc
      users/
        User.avsc
```

**Benefits:**
- Clear namespace organization
- Easy to find schemas
- Supports schema evolution per domain

### 2. Producer Configuration (Production-Ready)

```kotlin
private fun productionProducerProps(
    bootstrapServers: String,
    schemaRegistryUrl: String
): Map<String, Any> = mapOf(
    ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
    ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
    ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to KafkaAvroSerializer::class.java,
    "schema.registry.url" to schemaRegistryUrl,
    
    // Reliability
    ProducerConfig.ACKS_CONFIG to "all",                    // Wait for all replicas
    ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to true,      // Exactly-once semantics
    ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION to 5,
    
    // Performance
    ProducerConfig.LINGER_MS_CONFIG to 20,                  // Batch messages
    ProducerConfig.BATCH_SIZE_CONFIG to 65536,              // 64KB batches
    ProducerConfig.COMPRESSION_TYPE_CONFIG to "snappy",     // Compression
    
    // Schema Registry
    "auto.register.schemas" to false,                       // Don't auto-register (use CI/CD)
    "use.latest.version" to false,                          // Use explicit version
    
    // Error handling
    ProducerConfig.RETRIES_CONFIG to 3,
    ProducerConfig.MAX_BLOCK_MS_CONFIG to 60000
)
```

### 3. Consumer Configuration (Production-Ready)

```kotlin
private fun productionConsumerProps(
    bootstrapServers: String,
    schemaRegistryUrl: String,
    groupId: String
): Map<String, Any> = mapOf(
    ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
    ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
    ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to KafkaAvroDeserializer::class.java,
    ConsumerConfig.GROUP_ID_CONFIG to groupId,
    
    // Offset management
    ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",  // or "latest" for new consumers
    ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,     // Manual commit for reliability
    
    // Schema Registry
    "schema.registry.url" to schemaRegistryUrl,
    "specific.avro.reader" to true,
    "use.latest.version" to true,                            // Use latest compatible schema
    
    // Performance
    ConsumerConfig.FETCH_MIN_BYTES_CONFIG to 1,
    ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG to 500,
    ConsumerConfig.MAX_POLL_RECORDS_CONFIG to 500,
    
    // Session management
    ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG to 30000,
    ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG to 10000
)
```

### 4. Error Handling

```kotlin
@Bean
fun transactionListenerContainerFactory(
    transactionConsumerFactory: ConsumerFactory<String, Transaction>
): ConcurrentKafkaListenerContainerFactory<String, Transaction> {
    val factory = ConcurrentKafkaListenerContainerFactory<String, Transaction>()
    factory.consumerFactory = transactionConsumerFactory
    
    // Error handling
    factory.setCommonErrorHandler(
        DefaultErrorHandler(
            DeadLetterPublishingRecoverer(kafkaTemplate),
            FixedBackOff(1000L, 3)  // Retry 3 times with 1s delay
        )
    )
    
    // Concurrency
    factory.concurrency = 3  // 3 consumer threads
    
    return factory
}
```

### 5. Schema Registry Configuration

**application.yml:**
```yaml
schema:
  registry:
    url: http://schema-registry:8081
    # Optional: for multiple registries
    # urls: http://schema-registry-1:8081,http://schema-registry-2:8081
    
kafka:
  producer:
    properties:
      # Don't auto-register schemas in production
      auto.register.schemas: false
      # Use specific schema version
      use.latest.version: false
      
  consumer:
    properties:
      # Use latest compatible schema
      use.latest.version: true
      # Specific reader for type safety
      specific.avro.reader: true
```

---

## Common Patterns

### Pattern 1: One Producer, Multiple Consumers

```kotlin
// Single producer for transactions
@Bean
fun transactionProducer() = // ... single factory

// Multiple consumers with different groups
@Bean
fun fraudDetectorConsumer() = // ... group: "fraud-detector"

@Bean
fun analyticsConsumer() = // ... group: "analytics-service"

@Bean
fun auditConsumer() = // ... group: "audit-service"
```

### Pattern 2: Multiple Topics, Same Schema

```kotlin
// Same Transaction schema, different topics
transactionTemplate.send("transactions-us", key, tx)
transactionTemplate.send("transactions-eu", key, tx)
transactionTemplate.send("transactions-asia", key, tx)
```

### Pattern 3: Schema Evolution

```kotlin
// V1: Transaction (transactionId, amount, currency)
// V2: Transaction (transactionId, amount, currency, merchantId) - backward compatible

// Consumer can handle both:
@KafkaListener(topics = ["transactions-avro"])
fun onTransaction(record: ConsumerRecord<String, Transaction>) {
    val tx = record.value()
    // merchantId is optional - works with both v1 and v2
    val merchant = tx.merchantId ?: "unknown"
}
```

---

## Summary

### Current Setup: ✅ Good for Single Schema
- Type-safe with specific classes
- Schema generation automated
- Schema Registry integrated

### For Multiple Schemas: Use Approach 1
- Named factories for each schema type
- Shared base configuration
- Clear separation of concerns

### Production Checklist:
- ✅ Use specific Avro classes (not GenericRecord)
- ✅ Disable auto-register schemas (use CI/CD)
- ✅ Configure error handling
- ✅ Set appropriate retry/backoff
- ✅ Use manual offset commits for critical consumers
- ✅ Monitor schema registry compatibility
- ✅ Organize schemas by domain/namespace

