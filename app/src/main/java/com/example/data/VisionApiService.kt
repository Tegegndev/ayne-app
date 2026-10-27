package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class VisionApiService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {

    suspend fun seeAndDescribe(
        endpointUrl: String,
        imageBase64: String,
        audioBase64: String? = null,
        customVoice: String = "am-hamen",
        mode: String = "general",
        includeAudio: Boolean = true
    ): Result<VisionDescribeResponse> = withContext(Dispatchers.IO) {
        try {
            val jsonPayload = JSONObject().apply {
                put("imageBase64", imageBase64)
                if (!audioBase64.isNullOrBlank()) {
                    put("audioBase64", audioBase64)
                }
                put("customVoice", customVoice)
                put("mode", mode)
                put("includeAudio", includeAudio)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonPayload.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(endpointUrl)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody.isNullOrBlank()) {
                val errorMsg = "API returned HTTP ${response.code}: ${responseBody?.take(150) ?: "Empty response"}"
                return@withContext Result.failure(IOException(errorMsg))
            }

            val parsed = VisionDescribeResponse.fromJson(responseBody)
            Result.success(parsed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
