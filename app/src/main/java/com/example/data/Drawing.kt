package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "drawings")
data class Drawing(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val drawingData: String, // String representation of drawing path coordinates
    val timestamp: Long = System.currentTimeMillis()
)
