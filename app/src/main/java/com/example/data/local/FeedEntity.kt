package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "feeds")
data class FeedEntity(
    @PrimaryKey
    val url: String,
    val title: String,
    val category: String,
    val description: String = "",
    val iconUrl: String = "",
    val isPreferred: Boolean = false,
    val isEnabled: Boolean = true,
    val isCustom: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
)
