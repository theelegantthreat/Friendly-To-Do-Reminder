package com.example.api

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiService {
    private const val TAG = "GeminiService"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun generateHomeAdvice(
        prompt: String,
        chatHistory: List<Pair<String, Boolean>> = emptyList(),
        enableSearch: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "API Key is missing or not configured. Please add your GEMINI_API_KEY in the AI Studio Secrets Panel."
        }

        try {
            val root = JSONObject()

            // System Instruction
            val systemInstruction = JSONObject().apply {
                val parts = JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", "You are a warm and helpful home assistant. Your job is to help the user manage their household chores, reminders, gardening, cooking, and general home repairs. Keep answers encouraging, clean, and styled with brief, readable bullet points or numbered lists. Be friendly, empathetic, and enthusiastic about keeping a clean and happy home!")
                    })
                }
                put("parts", parts)
            }
            root.put("systemInstruction", systemInstruction)

            // Contents (History + current prompt)
            val contentsArray = JSONArray()

            // Add history
            for (turn in chatHistory) {
                val contentObj = JSONObject()
                contentObj.put("role", if (turn.second) "user" else "model")
                val partsArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", turn.first)
                    })
                }
                contentObj.put("parts", partsArray)
                contentsArray.put(contentObj)
            }

            // Add current prompt
            val currentContentObj = JSONObject().apply {
                put("role", "user")
                val partsArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", prompt)
                    })
                }
                put("parts", partsArray)
            }
            contentsArray.put(currentContentObj)
            root.put("contents", contentsArray)

            // Tools (Search Grounding)
            if (enableSearch) {
                val toolsArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("googleSearch", JSONObject())
                    })
                }
                root.put("tools", toolsArray)
            }

            // Generation config
            val generationConfig = JSONObject().apply {
                put("temperature", 0.7)
            }
            root.put("generationConfig", generationConfig)

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBodyString = root.toString()
            val requestBody = requestBodyString.toRequestBody(mediaType)

            val url = "$BASE_URL?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    Log.e(TAG, "API call failed: ${response.code} $errBody")
                    return@withContext "Error: Failed to fetch advice from Gemini (${response.code})."
                }

                val resBody = response.body?.string() ?: return@withContext "Empty response from server"
                val resJson = JSONObject(resBody)
                val candidates = resJson.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val candidate = candidates.getJSONObject(0)
                    val content = candidate.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        var responseText = parts.getJSONObject(0).optString("text", "")
                        
                        // Check for Grounding Metadata to append citations
                        val groundingMetadata = candidate.optJSONObject("groundingMetadata")
                        if (groundingMetadata != null) {
                            val groundingChunks = groundingMetadata.optJSONArray("groundingChunks")
                            if (groundingChunks != null && groundingChunks.length() > 0) {
                                responseText += "\n\n🌐 **Sources & Citations:**"
                                for (i in 0 until groundingChunks.length()) {
                                    val chunk = groundingChunks.getJSONObject(i)
                                    val web = chunk.optJSONObject("web")
                                    if (web != null) {
                                        val title = web.optString("title", "Source")
                                        val uri = web.optString("uri", "")
                                        if (uri.isNotEmpty()) {
                                            responseText += "\n• $title: $uri"
                                        }
                                    }
                                }
                            }
                        }
                        
                        return@withContext responseText
                    }
                }
                return@withContext "I am here to help, but I couldn't generate a response. Can you try rephrasing?"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during call", e)
            return@withContext "Error: ${e.localizedMessage ?: "Unknown network error occurred."}"
        }
    }
}
