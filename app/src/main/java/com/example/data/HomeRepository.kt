package com.example.data

import kotlinx.coroutines.flow.Flow

class HomeRepository(private val homeDao: HomeDao) {
    val allTasks: Flow<List<HomeTask>> = homeDao.getAllTasks()
    val allNotes: Flow<List<HomeNote>> = homeDao.getAllNotes()
    val allDrawings: Flow<List<Drawing>> = homeDao.getAllDrawings()

    suspend fun insertTask(task: HomeTask): Long = homeDao.insertTask(task)
    suspend fun updateTask(task: HomeTask) = homeDao.updateTask(task)
    suspend fun deleteTask(task: HomeTask) = homeDao.deleteTask(task)
    suspend fun deleteTaskById(taskId: Int) = homeDao.deleteTaskById(taskId)

    suspend fun insertNote(note: HomeNote): Long = homeDao.insertNote(note)
    suspend fun updateNote(note: HomeNote) = homeDao.updateNote(note)
    suspend fun deleteNote(note: HomeNote) = homeDao.deleteNote(note)
    suspend fun deleteNoteById(noteId: Int) = homeDao.deleteNoteById(noteId)

    suspend fun getDrawingById(id: Int): Drawing? = homeDao.getDrawingById(id)
    suspend fun insertDrawing(drawing: Drawing): Long = homeDao.insertDrawing(drawing)
    suspend fun deleteDrawing(drawing: Drawing) = homeDao.deleteDrawing(drawing)
    suspend fun deleteDrawingById(drawingId: Int) = homeDao.deleteDrawingById(drawingId)
}
