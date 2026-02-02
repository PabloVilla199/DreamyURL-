package es.unizar.urlshortener.infrastructure.repositories

import es.unizar.urlshortener.core.QrFormat
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration
import java.util.Base64

class QrCodeRepositoryServiceImplTest {

    private lateinit var redisTemplate: StringRedisTemplate
    private lateinit var valueOperations: ValueOperations<String, String>
    private lateinit var service: QrCodeRepositoryServiceImpl

    @BeforeEach
    fun setUp() {
        redisTemplate = mock()
        valueOperations = mock()
        whenever(redisTemplate.opsForValue()).thenReturn(valueOperations)
        service = QrCodeRepositoryServiceImpl(redisTemplate)
    }

    @Test
    fun `save stores encoded image in redis`() {
        val url = "http://example.com"
        val bytes = byteArrayOf(1, 2, 3)
        val expectedEncoded = Base64.getEncoder().encodeToString(bytes)

        service.save(url, 200, QrFormat.PNG, bytes)

        verify(valueOperations).set(
            anyString(), 
            eq(expectedEncoded),
            any<Duration>() 
        )
    }

    @Test
    fun `find returns decoded bytes when present`() {
        val url = "http://example.com"
        val bytes = byteArrayOf(1, 2, 3)
        val encoded = Base64.getEncoder().encodeToString(bytes)

        whenever(valueOperations.get(anyString())).thenReturn(encoded)

        val result = service.find(url, 200, QrFormat.PNG)

        assertArrayEquals(bytes, result)
    }

    @Test
    fun `find returns null when missing`() {
        whenever(valueOperations.get(anyString())).thenReturn(null)

        val result = service.find(url = "http://missing.com", size = 200, format = QrFormat.PNG)

        assertNull(result)
    }
}
