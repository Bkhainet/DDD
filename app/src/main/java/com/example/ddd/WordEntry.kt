package com.example.ddd

import kotlinx.serialization.Serializable

@Serializable
data class WordEntry(
    val Artikel: String?,
    val Word: String,
    val Translation: String,
    val Level: String,
    val isUsed: Int = 0,
    val ErrorFlag: Int = 0
)
