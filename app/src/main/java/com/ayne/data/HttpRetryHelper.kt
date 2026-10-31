package com.ayne.data

import kotlinx.coroutines.delay
import java.io.IOException

class HttpException(val statusCode: Int, message: String) : IOException(message)

object HttpRetryHelper {
    suspend fun <T> executeWithRetry(
        maxAttempts: Int = 2,
        initialDelayMs: Long = 400L,
        backoffMultiplier: Double = 3.0,
        operation: suspend (attempt: Int) -> Result<T>
    ): Result<T> {
        var currentDelay = initialDelayMs
        var lastResult: Result<T>? = null

        for (attempt in 1..maxAttempts) {
            val result = operation(attempt)
            if (result.isSuccess) {
                return result
            }

            lastResult = result
            val exception = result.exceptionOrNull()

            val isRecoverable = when (exception) {
                is HttpException -> exception.statusCode in 500..599
                is IOException -> true
                else -> false
            }

            if (!isRecoverable || attempt == maxAttempts) {
                return result
            }

            delay(currentDelay)
            currentDelay = (currentDelay * backoffMultiplier).toLong()
        }

        return lastResult ?: Result.failure(IOException("Operation failed after $maxAttempts attempts"))
    }
}
