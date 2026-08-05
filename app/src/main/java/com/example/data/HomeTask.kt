package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class HomeTask(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val notes: String = "",
    val isCompleted: Boolean = false,
    val dueDate: Long? = null, // timestamp
    val reminderTime: Long? = null, // timestamp
    val category: String = "Home", // Home, Garden, Kitchen, Cleaning, Maintenance, etc.
    val hasDrawingAttachment: Boolean = false,
    val attachedDrawingId: Int? = null,
    val priority: String = "Medium", // "Low", "Medium", "High"
    val frequency: String = "Once", // "Once", "Daily", "Weekly", "Monthly"
    val completedAt: String? = null // "YYYY-MM-DD, HH:MM:SS"
)
