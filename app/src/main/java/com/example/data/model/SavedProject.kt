package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_projects")
data class SavedProject(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val originalVideoName: String,
    val videoPath: String,
    val thumbnailPath: String,
    val durationSeconds: Long,
    val screenshotCount: Int,
    val fileSizeBytes: Long,
    val resolution: String,
    val createdAt: Long = System.currentTimeMillis()
)
