package com.gemini.notion.data

data class Note(
    val id: Int = 0,
    val title: String,
    val content: String,
    val lastUpdated: Long = System.currentTimeMillis()
)
