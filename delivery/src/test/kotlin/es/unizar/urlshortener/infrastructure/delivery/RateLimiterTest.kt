@file:Suppress("NonAsciiCharacters", "SpellCheckingInspection")

package es.unizar.urlshortener.infrastructure.delivery

import es.unizar.urlshortener.core.RateLimitStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

/**
 * Unit tests for [RateLimiterServiceImpl].
 *
 * These tests validate the token bucket logic (Bucket4j) used to control
 * Safe Browsing API rate limits.
 */
class RateLimiterServiceImplTest {

    @Test
    fun `should initialize bucket with configured capacity`() {
        val limiter = RateLimiterServiceImpl(5, 5, 1)
        val status: RateLimitStatus = limiter.getStatus()

        assertEquals(5, status.remainingTokens)
        assertFalse(status.isLimitExceeded)
        assertNotNull(status.resetTime)
    }

    @Test
    fun `should decrease remaining tokens after consumption`() {
        val limiter = RateLimiterServiceImpl(3, 3, 1)
        assertTrue(limiter.tryConsume()) 

        val status = limiter.getStatus()
        assertEquals(2, status.remainingTokens)
    }

    @Test
    fun `should not allow consumption when bucket is empty`() {
        val limiter = RateLimiterServiceImpl(1, 1, 60)
        
        assertTrue(limiter.tryConsume()) 
        assertFalse(limiter.tryConsume()) 
        
        val status = limiter.getStatus()
        assertTrue(status.isLimitExceeded)
        assertEquals(0, status.remainingTokens)
    }

    @Test
    fun `should refill tokens after configured interval`() {
        val limiter = RateLimiterServiceImpl(1, 1, 1)
        
        limiter.tryConsume() 
        assertEquals(0, limiter.getStatus().remainingTokens)

        Thread.sleep(1100) 
        
        val status = limiter.getStatus()
        assertEquals(1, status.remainingTokens, "El token debería haberse regenerado tras 1.1s")
        
        assertTrue(limiter.tryConsume())
    }

    @Test
    fun `getStatus should return future reset time when empty`() {
        val limiter = RateLimiterServiceImpl(1, 1, 60)
        limiter.tryConsume()
        
        val status = limiter.getStatus()
        val now = OffsetDateTime.now()

        assertTrue(status.isLimitExceeded)
        assertEquals(0, status.remainingTokens) 
        assertFalse(status.resetTime.isBefore(now))
    }
}
