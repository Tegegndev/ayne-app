package com.ayne.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object HttpClientProvider {
    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun buildJsonPostRequest(
        url: String,
        jsonBody: JSONObject,
        headers: Map<String, String> = emptyMap()
    ): Request {
        val requestBody: RequestBody = jsonBody.toString().toRequestBody(jsonMediaType)
        val builder = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")

        for ((key, value) in headers) {
            builder.addHeader(key, value)
        }

        return builder.build()
    }
}
