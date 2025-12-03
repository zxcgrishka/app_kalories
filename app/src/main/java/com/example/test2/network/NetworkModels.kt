package com.example.test2.network

data class Token(
    val access_token: String,
    val token_type: String
)

data class User(
    val id: Int,
    val username: String,
    val is_active: String,
    val lastModified: Long
)

