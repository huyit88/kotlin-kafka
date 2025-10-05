package com.example

import kotlinx.serialization.Serializable

@Serializable
data class LogEvent(
    val app: String,
    val level: String,
    val message: String,
    val tsEpochMs: Long
)