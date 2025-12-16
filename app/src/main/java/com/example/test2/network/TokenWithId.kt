package com.example.test2.network

data class TokenWithId(
    val access_token: String,
    val token_type: String,
    val user_id: Long
)