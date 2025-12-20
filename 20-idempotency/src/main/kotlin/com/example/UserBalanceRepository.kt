package com.example

import org.springframework.data.repository.CrudRepository

interface UserBalanceRepository : CrudRepository<UserBalance, String>