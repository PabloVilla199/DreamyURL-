package es.unizar.urlshortener.infrastructure.repositories

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.HashOperations
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations

class RedisStatsRepositoryTest {

    private lateinit var redisTemplate: StringRedisTemplate
    private lateinit var valueOps: ValueOperations<String, String>
    private lateinit var hashOps: HashOperations<String, String, String>
    private lateinit var repository: RedisStatsRepository

    @BeforeEach
    fun setUp() {
        redisTemplate = mock()
        valueOps = mock()
        hashOps = mock()

        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
        whenever(redisTemplate.opsForHash<String, String>()).thenReturn(hashOps)

        repository = RedisStatsRepository(redisTemplate)
    }

    @Test
    fun `incrUrlTotal increments the correct key`() {
        repository.incrUrlTotal("hash1")
        verify(valueOps).increment("stats:url:hash1:total")
    }

    @Test
    fun `incrUrlCountry increments the correct hash key`() {
        repository.incrUrlCountry("hash1", "ES")
        verify(hashOps).increment("stats:url:hash1:countries", "ES", 1)
    }

    @Test
    fun `incrUrlCity handles city with country code`() {
        repository.incrUrlCity("hash1", "Madrid", "ES")
        verify(hashOps).increment("stats:url:hash1:cities", "Madrid|ES", 1)
    }

    @Test
    fun `incrUrlCity handles city without country code`() {
        repository.incrUrlCity("hash1", "Unknown", null)
        verify(hashOps).increment("stats:url:hash1:cities", "Unknown", 1)
    }

    @Test
    fun `incrSystemTotal increments system key`() {
        repository.incrSystemTotal()
        verify(valueOps).increment("stats:system:total")
    }

    @Test
    fun `incrSystemCountry increments system hash`() {
        repository.incrSystemCountry("FR")
        verify(hashOps).increment("stats:system:countries", "FR", 1)
    }

    @Test
    fun `incrSystemCity increments system city hash`() {
        repository.incrSystemCity("Paris", "FR")
        verify(hashOps).increment("stats:system:cities", "Paris|FR", 1)
    }

    @Test
    fun `getUrlAggregates returns correct data structure`() {
        val id = "hash1"
        whenever(valueOps.get("stats:url:$id:total")).thenReturn("10")
        whenever(hashOps.entries("stats:url:$id:countries")).thenReturn(mapOf("ES" to "6", "FR" to "4"))
        whenever(hashOps.entries("stats:url:$id:cities")).thenReturn(mapOf("Madrid|ES" to "6"))

        val result = repository.getUrlAggregates(id)

        assertEquals(10L, result.total)
        assertEquals(6L, result.countries["ES"])
        assertEquals(4L, result.countries["FR"])
        assertEquals(6L, result.cities["Madrid|ES"])
    }

    @Test
    fun `getUrlAggregates handles nulls and empty data`() {
        val id = "empty"
        whenever(valueOps.get("stats:url:$id:total")).thenReturn(null)
        whenever(hashOps.entries("stats:url:$id:countries")).thenReturn(emptyMap())
        whenever(hashOps.entries("stats:url:$id:cities")).thenReturn(emptyMap())

        val result = repository.getUrlAggregates(id)

        assertEquals(0L, result.total)
        assertNotNull(result.countries)
        assertNotNull(result.cities)
    }

    @Test
    fun `getSystemAggregates returns correct data`() {
        whenever(valueOps.get("stats:system:total")).thenReturn("100")
        whenever(hashOps.entries("stats:system:countries")).thenReturn(mapOf("US" to "50"))
        whenever(hashOps.entries("stats:system:cities")).thenReturn(mapOf("NY|US" to "50"))

        val result = repository.getSystemAggregates()

        assertEquals(100L, result.total)
        assertEquals(50L, result.countries["US"])
        assertEquals(50L, result.cities["NY|US"])
    }
}
