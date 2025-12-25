package com.example

import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicLong
import java.time.Instant

@Service
class HotMetricsService {
    private val startTime = Instant.now()
    private val hotMessageCount = AtomicLong(0)

    fun incrementHotCount() {
        hotMessageCount.incrementAndGet()
    }

    fun getCount(): Long {
        return hotMessageCount.get()
    }

    fun getRate(): Double {
        val elapsedSeconds = java.time.Duration.between(startTime, Instant.now()).seconds
        return if (elapsedSeconds > 0) {
            hotMessageCount.get().toDouble() / elapsedSeconds
        } else {
            0.0
        }
    }
}

