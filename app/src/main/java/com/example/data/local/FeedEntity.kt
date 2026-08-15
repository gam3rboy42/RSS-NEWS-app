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
    val preferredScope: String = SCOPE_ALL, // "ALL" or "CATEGORY"
    val isEnabled: Boolean = true,
    val isCustom: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    companion object {
        const val SCOPE_ALL = "ALL"
        const val SCOPE_CATEGORY = "CATEGORY"
    }

    val isCategoryOnlyPreferred: Boolean
        get() = isPreferred && preferredScope.equals(SCOPE_CATEGORY, ignoreCase = true)

    val isGlobalPreferred: Boolean
        get() = isPreferred && (preferredScope.equals(SCOPE_ALL, ignoreCase = true) || preferredScope.isBlank())
}
