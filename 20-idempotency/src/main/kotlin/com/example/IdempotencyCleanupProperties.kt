package com.example

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "idempotency.cleanup")
class IdempotencyCleanupProperties {
    var ttlMinutes: Long = 10
    var intervalMs: Long = 60000
}
