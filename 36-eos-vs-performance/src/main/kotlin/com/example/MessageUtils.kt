package com.example

object MessageUtils {
    private const val EVENT_TIME_PREFIX = "eventTime="

    fun extractEventTime(value: String): Long {
        val index = value.indexOf(EVENT_TIME_PREFIX)
        if (index != -1) {
            val start = index + EVENT_TIME_PREFIX.length
            val end = value.indexOf('|', start).let { if (it == -1) value.length else it }
            return value.substring(start, end).toLongOrNull() ?: System.currentTimeMillis()
        }
        return System.currentTimeMillis()
    }

    fun calculateLag(eventTime: Long): Long {
        return System.currentTimeMillis() - eventTime
    }
}

