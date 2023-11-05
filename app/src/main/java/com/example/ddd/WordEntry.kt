package com.example.ddd

import kotlinx.serialization.Serializable

@Serializable
data class WordEntry(
    val Artikel: String?,
    val Word: String,
    val Translation: String
)