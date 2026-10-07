package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.SavedProject
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM saved_projects ORDER BY createdAt DESC")
    fun getAllProjects(): Flow<List<SavedProject>>

    @Query("SELECT * FROM saved_projects WHERE id = :id LIMIT 1")
    suspend fun getProjectById(id: Long): SavedProject?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: SavedProject): Long

    @Delete
    suspend fun deleteProject(project: SavedProject)

    @Query("DELETE FROM saved_projects WHERE id = :id")
    suspend fun deleteProjectById(id: Long)
}
