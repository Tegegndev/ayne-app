package com.example

import com.example.data.HttpException
import com.example.data.HttpRetryHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class HttpRetryHelperTest {

    @Test
    fun testSuccessDoesNotRetry() = runBlocking {
        var callCount = 0
        val result = HttpRetryHelper.executeWithRetry(initialDelayMs = 10) {
            callCount++
            Result.success("ok")
        }

        assertTrue(result.isSuccess)
        assertEquals("ok", result.getOrNull())
        assertEquals(1, callCount)
    }

    @Test
    fun testClientError4xxDoesNotRetry() = runBlocking {
        var callCount = 0
        val result = HttpRetryHelper.executeWithRetry<String>(initialDelayMs = 10) {
            callCount++
            Result.failure(HttpException(400, "Bad Request"))
        }

        assertTrue(result.isFailure)
        assertEquals(1, callCount)
    }

    @Test
    fun testServerError5xxRetriesAndRecovers() = runBlocking {
        var callCount = 0
        val result = HttpRetryHelper.executeWithRetry(initialDelayMs = 10) { attempt ->
            callCount++
            if (attempt == 1) {
                Result.failure(HttpException(503, "Service Unavailable"))
            } else {
                Result.success("recovered")
            }
        }

        assertTrue(result.isSuccess)
        assertEquals("recovered", result.getOrNull())
        assertEquals(2, callCount)
    }

    @Test
    fun testNetworkIoExceptionRetries() = runBlocking {
        var callCount = 0
        val result = HttpRetryHelper.executeWithRetry<String>(initialDelayMs = 10) {
            callCount++
            Result.failure(IOException("Airplane mode"))
        }

        assertTrue(result.isFailure)
        assertEquals(2, callCount)
    }
}
