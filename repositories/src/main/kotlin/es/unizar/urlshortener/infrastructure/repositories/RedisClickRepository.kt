package es.unizar.urlshortener.infrastructure.repositories

import com.fasterxml.jackson.databind.ObjectMapper
import es.unizar.urlshortener.core.ClickInfo
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.stereotype.Repository

/**
 * Redis-backed implementation of ClickRepository.
 * Stores ClickInfo as JSON in a Redis list per shortUrlId.
 */
@Repository
open class RedisClickRepository(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper
) : ClickRepository {

    override fun save(click: ClickInfo) {
        val key = keyFor(click.shortUrlId)
        val value = objectMapper.writeValueAsString(click)
        redisTemplate.opsForList().leftPush(key, value)
    }

    override fun findByShortUrlId(shortUrlId: String): List<ClickInfo> {
        val key = keyFor(shortUrlId)
        val values = redisTemplate.opsForList().range(key, 0, -1) ?: return emptyList()
        return values.mapNotNull {
            try {
                objectMapper.readValue(it, ClickInfo::class.java)
            } catch (ex: Exception) {
                null
            }
        }
    }

    private fun keyFor(shortUrlId: String) = "clicks:$shortUrlId"
}
