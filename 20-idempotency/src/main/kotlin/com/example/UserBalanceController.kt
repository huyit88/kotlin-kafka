package com.example

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping
class UserBalanceController(
    private val repo: UserBalanceRepository
) {
    @GetMapping("/balances/{userId}")
    fun getBalance(@PathVariable userId: String): UserBalanceDto {
        val userBalance = repo.findById(userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User $userId not found") }
        return UserBalanceDto(userBalance.userId, userBalance.balance)
    }
}