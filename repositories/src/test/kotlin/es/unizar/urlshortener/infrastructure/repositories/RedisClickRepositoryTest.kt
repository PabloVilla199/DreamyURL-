package es.unizar.urlshortener.infrastructure.repositories

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import es.unizar.urlshortener.core.ClickInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.ListOperations
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Instant

class RedisClickRepositoryTest {

    private val objectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    @Test
    fun `save calls leftPush`() {
        val redisTemplate: StringRedisTemplate = mock()
        val listOps = mock<ListOperations<String, String>>()
        whenever(redisTemplate.opsForList()).thenReturn(listOps)

        val repo = RedisClickRepository(redisTemplate, objectMapper)
        val click = ClickInfo("hash1", "1.2.3.4", "ES", "", "", "", Instant.now())

        repo.save(click)

        verify(listOps).leftPush(eq("clicks:hash1"), any())
    }

    @Test
    fun `findByShortUrlId returns deserialized objects`() {
        val redisTemplate: StringRedisTemplate = mock()
        val listOps = mock<ListOperations<String, String>>()
        whenever(redisTemplate.opsForList()).thenReturn(listOps)

        val repo = RedisClickRepository(redisTemplate, objectMapper)

        val click = ClickInfo("hash1", "1.1.1.1", "US", null, null, null, Instant.now())
        val json = objectMapper.writeValueAsString(click)

        whenever(listOps.range("clicks:hash1", 0, -1)).thenReturn(listOf(json))

        val result = repo.findByShortUrlId("hash1")

        assertEquals(1, result.size)
        assertEquals("1.1.1.1", result[0].ip)
    }

    @Test
    fun `findByShortUrlId returns empty list when redis returns null`() {
        val redisTemplate: StringRedisTemplate = mock()
        val listOps = mock<ListOperations<String, String>>()
        whenever(redisTemplate.opsForList()).thenReturn(listOps)
        val repo = RedisClickRepository(redisTemplate, objectMapper)

        // Mock null return from Redis
        whenever(listOps.range("clicks:unknown", 0, -1)).thenReturn(null)

        val result = repo.findByShortUrlId("unknown")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `findByShortUrlId ignores malformed json`() {
        val redisTemplate: StringRedisTemplate = mock()
        val listOps = mock<ListOperations<String, String>>()
        whenever(redisTemplate.opsForList()).thenReturn(listOps)
        val repo = RedisClickRepository(redisTemplate, objectMapper)

        // One valid JSON, one corrupted string
        val validClick = ClickInfo("hash1", "8.8.8.8", "FR", null, null, null, Instant.now())
        val validJson = objectMapper.writeValueAsString(validClick)
        val badJson = "{ invalid_json: ]"

        whenever(listOps.range("clicks:hash1", 0, -1)).thenReturn(listOf(validJson, badJson))

        val result = repo.findByShortUrlId("hash1")

        assertEquals(1, result.size, "Should filter out the bad JSON")
        assertEquals("8.8.8.8", result[0].ip)
    }
}
