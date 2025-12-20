package com.example

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "user_balance")
data class UserBalance(
    @Id
    val userId: String,
    val balance: Long
)