package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HomeDao {
    // Tasks
    @Query("SELECT * FROM tasks ORDER BY isCompleted ASC, dueDate ASC, id DESC")
    fun getAllTasks(): Flow<List<HomeTask>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: HomeTask): Long

    @Update
    suspend fun updateTask(task: HomeTask)

    @Delete
    suspend fun deleteTask(task: HomeTask)

    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun deleteTaskById(taskId: Int)

    // Notes
    @Query("SELECT * FROM notes ORDER BY timestamp DESC")
    fun getAllNotes(): Flow<List<HomeNote>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: HomeNote): Long

    @Update
    suspend fun updateNote(note: HomeNote)

    @Delete
    suspend fun deleteNote(note: HomeNote)

    @Query("DELETE FROM notes WHERE id = :noteId")
    suspend fun deleteNoteById(noteId: Int)

    // Drawings
    @Query("SELECT * FROM drawings ORDER BY timestamp DESC")
    fun getAllDrawings(): Flow<List<Drawing>>

    @Query("SELECT * FROM drawings WHERE id = :id LIMIT 1")
    suspend fun getDrawingById(id: Int): Drawing?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDrawing(drawing: Drawing): Long

    @Delete
    suspend fun deleteDrawing(drawing: Drawing)

    @Query("DELETE FROM drawings WHERE id = :drawingId")
    suspend fun deleteDrawingById(drawingId: Int)
}
