package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.GeminiService
import com.example.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject
import org.json.JSONArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: HomeRepository

    val tasks: StateFlow<List<HomeTask>>
    val notes: StateFlow<List<HomeNote>>
    val drawings: StateFlow<List<Drawing>>

    val isSmartSuggesting = MutableStateFlow(false)
    val smartSuggestions = MutableStateFlow<List<HomeTask>>(emptyList())

    init {
        val database = HomeDatabase.getDatabase(application)
        repository = HomeRepository(database.homeDao())

        tasks = repository.allTasks.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        notes = repository.allNotes.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        drawings = repository.allDrawings.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    // Task Actions
    fun addTask(
        title: String,
        notes: String = "",
        dueDate: Long? = null,
        reminderTime: Long? = null,
        category: String = "Home",
        hasDrawingAttachment: Boolean = false,
        attachedDrawingId: Int? = null,
        priority: String = "Medium",
        frequency: String = "Once"
    ) {
        viewModelScope.launch {
            repository.insertTask(
                HomeTask(
                    title = title,
                    notes = notes,
                    dueDate = dueDate,
                    reminderTime = reminderTime,
                    category = category,
                    hasDrawingAttachment = hasDrawingAttachment,
                    attachedDrawingId = attachedDrawingId,
                    priority = priority,
                    frequency = frequency
                )
            )
        }
    }

    fun toggleTask(task: HomeTask) {
        viewModelScope.launch {
            val nextCompleted = !task.isCompleted
            val completedAtTime = if (nextCompleted) {
                SimpleDateFormat("yyyy-MM-dd, hh:mm:ss a", Locale.getDefault()).format(Date())
            } else {
                null
            }
            repository.updateTask(
                task.copy(
                    isCompleted = nextCompleted,
                    completedAt = completedAtTime
                )
            )

            if (nextCompleted) {
                val freq = task.frequency
                if (freq == "Daily" || freq == "Weekly" || freq == "Monthly") {
                    val calendar = java.util.Calendar.getInstance()
                    val now = System.currentTimeMillis()
                    val baseTime = if (task.dueDate != null && task.dueDate > now) {
                        task.dueDate
                    } else {
                        now
                    }
                    calendar.timeInMillis = baseTime

                    val rc = java.util.Calendar.getInstance()
                    val hasReminder = task.reminderTime != null
                    if (hasReminder) {
                        rc.timeInMillis = task.reminderTime!!
                    }

                    when (freq) {
                        "Daily" -> {
                            calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
                            if (hasReminder) rc.add(java.util.Calendar.DAY_OF_YEAR, 1)
                        }
                        "Weekly" -> {
                            calendar.add(java.util.Calendar.WEEK_OF_YEAR, 1)
                            if (hasReminder) rc.add(java.util.Calendar.WEEK_OF_YEAR, 1)
                        }
                        "Monthly" -> {
                            calendar.add(java.util.Calendar.MONTH, 1)
                            if (hasReminder) rc.add(java.util.Calendar.MONTH, 1)
                        }
                    }

                    val copyTask = task.copy(
                        id = 0,
                        isCompleted = false,
                        completedAt = null,
                        dueDate = calendar.timeInMillis,
                        reminderTime = if (hasReminder) rc.timeInMillis else null
                    )
                    repository.insertTask(copyTask)
                }
            }
        }
    }

    fun updateTask(task: HomeTask) {
        viewModelScope.launch {
            repository.updateTask(task)
        }
    }

    fun deleteTask(task: HomeTask) {
        viewModelScope.launch {
            repository.deleteTask(task)
        }
    }

    // Note Actions
    fun addNote(title: String, content: String) {
        viewModelScope.launch {
            repository.insertNote(HomeNote(title = title, content = content))
        }
    }

    fun updateNote(note: HomeNote) {
        viewModelScope.launch {
            repository.updateNote(note)
        }
    }

    fun deleteNote(note: HomeNote) {
        viewModelScope.launch {
            repository.deleteNote(note)
        }
    }

    // Drawing Actions
    fun saveDrawing(title: String, drawingData: String, onSaved: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val drawingId = repository.insertDrawing(Drawing(title = title, drawingData = drawingData))
            onSaved(drawingId.toInt())
        }
    }

    fun deleteDrawing(drawing: Drawing) {
        viewModelScope.launch {
            repository.deleteDrawing(drawing)
        }
    }

    // Smart Chore suggestions
    fun generateSmartChoreSuggestions() {
        viewModelScope.launch {
            isSmartSuggesting.value = true
            val prompt = """
                Based on common home management needs, generate 5 useful and highly specific household tasks or chores that typically need to be done at home.
                For example: 'Clean dishwasher filter', 'Check smoke detector batteries', 'Prune dead leaves from house plants'.
                Format the response strictly as a JSON array of objects with keys 'title', 'category', 'notes'.
                Keep categories simple: 'Cleaning', 'Maintenance', 'Garden', 'Kitchen', or 'General'.
                Do not include any other conversational filler text or markdown fences except the raw JSON array.
            """.trimIndent()

            val response = GeminiService.generateHomeAdvice(prompt, emptyList(), enableSearch = false)
            try {
                val cleanResponse = response.substringAfter("[").substringBeforeLast("]")
                val array = org.json.JSONArray("[$cleanResponse]")
                val list = mutableListOf<HomeTask>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        HomeTask(
                            title = obj.getString("title"),
                            category = obj.optString("category", "General"),
                            notes = obj.optString("notes", "")
                        )
                    )
                }
                smartSuggestions.value = list
            } catch (e: Exception) {
                // Fallback to high-quality presets
                smartSuggestions.value = listOf(
                    HomeTask(title = "Clean refrigerator coils", category = "Kitchen", notes = "Helps fridge run efficiently and last longer."),
                    HomeTask(title = "Declutter the hallway closet", category = "Cleaning", notes = "Sort out old jackets, shoes, and home items."),
                    HomeTask(title = "Water and prune yard plants", category = "Garden", notes = "Give garden beds a deep soak and trim dead stems."),
                    HomeTask(title = "Wipe kitchen cabinets", category = "Kitchen", notes = "Use a warm damp microfiber cloth with mild soap."),
                    HomeTask(title = "Deep-clean trash bins", category = "Cleaning", notes = "Wash outside trash cans to prevent mold and odors.")
                )
            } finally {
                isSmartSuggesting.value = false
            }
        }
    }

    fun dismissSuggestions() {
        smartSuggestions.value = emptyList()
    }

    // JSON export and import
    fun exportDataToJson(): String {
        val rootObj = JSONObject()
        
        val tasksArray = JSONArray()
        tasks.value.forEach { task ->
            val taskObj = JSONObject().apply {
                put("title", task.title)
                put("notes", task.notes)
                put("isCompleted", task.isCompleted)
                put("dueDate", task.dueDate ?: JSONObject.NULL)
                put("reminderTime", task.reminderTime ?: JSONObject.NULL)
                put("category", task.category)
                put("hasDrawingAttachment", task.hasDrawingAttachment)
                put("attachedDrawingId", task.attachedDrawingId ?: JSONObject.NULL)
                put("priority", task.priority)
                put("frequency", task.frequency)
                put("completedAt", task.completedAt ?: JSONObject.NULL)
            }
            tasksArray.put(taskObj)
        }
        rootObj.put("tasks", tasksArray)

        val notesArray = JSONArray()
        notes.value.forEach { note ->
            val noteObj = JSONObject().apply {
                put("title", note.title)
                put("content", note.content)
                put("timestamp", note.timestamp)
            }
            notesArray.put(noteObj)
        }
        rootObj.put("notes", notesArray)

        return rootObj.toString(4)
    }

    suspend fun importDataFromJson(jsonStr: String): Boolean {
        return try {
            val rootObj = JSONObject(jsonStr)
            
            val tasksArray = rootObj.optJSONArray("tasks")
            if (tasksArray != null) {
                for (i in 0 until tasksArray.length()) {
                    val taskObj = tasksArray.getJSONObject(i)
                    val task = HomeTask(
                        title = taskObj.getString("title"),
                        notes = taskObj.optString("notes", ""),
                        isCompleted = taskObj.optBoolean("isCompleted", false),
                        dueDate = if (taskObj.isNull("dueDate")) null else taskObj.getLong("dueDate"),
                        reminderTime = if (taskObj.isNull("reminderTime")) null else taskObj.getLong("reminderTime"),
                        category = taskObj.optString("category", "Home"),
                        hasDrawingAttachment = taskObj.optBoolean("hasDrawingAttachment", false),
                        attachedDrawingId = if (taskObj.isNull("attachedDrawingId")) null else taskObj.getInt("attachedDrawingId"),
                        priority = taskObj.optString("priority", "Medium"),
                        frequency = taskObj.optString("frequency", "Once"),
                        completedAt = if (taskObj.isNull("completedAt")) null else taskObj.getString("completedAt")
                    )
                    repository.insertTask(task)
                }
            }

            val notesArray = rootObj.optJSONArray("notes")
            if (notesArray != null) {
                for (i in 0 until notesArray.length()) {
                    val noteObj = notesArray.getJSONObject(i)
                    val note = HomeNote(
                        title = noteObj.getString("title"),
                        content = noteObj.optString("content", ""),
                        timestamp = noteObj.optLong("timestamp", System.currentTimeMillis())
                    )
                    repository.insertNote(note)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // SharedPreferences for GitHub Token and Gist ID
    private val prefs = getApplication<Application>().getSharedPreferences("gist_sync_prefs", android.content.Context.MODE_PRIVATE)

    fun getGithubToken(): String = prefs.getString("github_token", "") ?: ""
    fun getGistId(): String = prefs.getString("gist_id", "") ?: ""

    fun saveGistSettings(token: String, gistId: String) {
        prefs.edit().apply {
            putString("github_token", token)
            putString("gist_id", gistId)
            apply()
        }
    }

    // GitHub Gist Upload / Download
    suspend fun uploadToGist(): Result<String> = withContext(Dispatchers.IO) {
        val token = getGithubToken()
        val gistId = getGistId()
        if (token.isEmpty()) {
            return@withContext Result.failure(Exception("GitHub Personal Access Token (PAT) is empty!"))
        }

        val jsonContent = exportDataToJson()

        val client = OkHttpClient()
        val mediaType = "application/json; charset=utf-8".toMediaType()

        val fileName = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()).let { "backup-ToDo-$it.JSON" }

        val filesObj = JSONObject().apply {
            put(fileName, JSONObject().apply {
                put("content", jsonContent)
            })
        }
        val requestBodyObj = JSONObject().apply {
            put("description", "Friendly To Do Chore & Notes Backup")
            put("public", false)
            put("files", filesObj)
        }

        val requestBody = requestBodyObj.toString().toRequestBody(mediaType)

        val request = if (gistId.isEmpty()) {
            Request.Builder()
                .url("https://api.github.com/gists")
                .post(requestBody)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/vnd.github+json")
                .build()
        } else {
            Request.Builder()
                .url("https://api.github.com/gists/$gistId")
                .patch(requestBody)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/vnd.github+json")
                .build()
        }

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorMsg = response.body?.string() ?: "Unknown error"
                    return@withContext Result.failure(Exception("GitHub API Error: ${response.code} - $errorMsg"))
                }
                val resBody = response.body?.string() ?: ""
                val resObj = JSONObject(resBody)
                val newGistId = resObj.getString("id")
                if (gistId.isEmpty()) {
                    saveGistSettings(token, newGistId)
                }
                Result.success(newGistId)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadFromGist(): Result<Boolean> = withContext(Dispatchers.IO) {
        val token = getGithubToken()
        val gistId = getGistId()
        if (token.isEmpty()) {
            return@withContext Result.failure(Exception("GitHub Personal Access Token (PAT) is empty!"))
        }
        if (gistId.isEmpty()) {
            return@withContext Result.failure(Exception("Gist ID is empty! Please upload first or enter a Gist ID."))
        }

        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://api.github.com/gists/$gistId")
            .get()
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Accept", "application/vnd.github+json")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorMsg = response.body?.string() ?: "Unknown error"
                    return@withContext Result.failure(Exception("GitHub API Error: ${response.code} - $errorMsg"))
                }
                val resBody = response.body?.string() ?: ""
                val resObj = JSONObject(resBody)
                val filesObj = resObj.getJSONObject("files")
                
                var backupContent: String? = null
                val keys = filesObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (key.startsWith("backup-ToDo-", ignoreCase = true) && key.endsWith(".JSON", ignoreCase = true)) {
                        backupContent = filesObj.getJSONObject(key).getString("content")
                        break
                    }
                }

                if (backupContent == null) {
                    // Fallback to ToDo- pattern
                    val keysFallback = filesObj.keys()
                    while (keysFallback.hasNext()) {
                        val keyFallback = keysFallback.next()
                        if (keyFallback.startsWith("ToDo-", ignoreCase = true) && keyFallback.endsWith(".JSON", ignoreCase = true)) {
                            backupContent = filesObj.getJSONObject(keyFallback).getString("content")
                            break
                        }
                    }
                }
                
                if (backupContent == null) {
                    // Fallback to legacy filename
                    if (filesObj.has("friendly_todo_data.json")) {
                        backupContent = filesObj.getJSONObject("friendly_todo_data.json").getString("content")
                    }
                }

                if (backupContent == null) {
                    return@withContext Result.failure(Exception("Gist does not contain a backup-ToDo-YYYY-MM-DD.JSON file!"))
                }

                val success = importDataFromJson(backupContent)
                if (success) {
                    Result.success(true)
                } else {
                    Result.failure(Exception("Failed to parse or save imported JSON content."))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
