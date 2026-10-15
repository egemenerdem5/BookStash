package com.egemenerdem.bookstash.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey
    val id: String, // We use String because the Google Books API returns String IDs (e.g., "zyTCAlFPjgYC")
    val title: String,
    val authors: String, // We will store multiple authors as a comma-separated string for simplicity
    val description: String?, // Nullable, as some books lack summaries
    val coverUrl: String?,
    val pageCount: Int,
    val currentPage: Int = 0,
    val status: String, // "WANT_TO_READ", "CURRENTLY_READING", "COMPLETED"
    val dateAdded: Long = System.currentTimeMillis(),
    val dateFinished: Long? = null
)

