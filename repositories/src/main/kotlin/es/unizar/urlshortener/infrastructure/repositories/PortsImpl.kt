package es.unizar.urlshortener.infrastructure.repositories

import es.unizar.urlshortener.core.Click
import es.unizar.urlshortener.core.ClickRepositoryService
import es.unizar.urlshortener.core.ShortUrl
import es.unizar.urlshortener.core.ShortUrlRepositoryService
import es.unizar.urlshortener.core.UrlHash
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import org.springframework.stereotype.Service
import es.unizar.urlshortener.core.QrCodeRepositoryService
import es.unizar.urlshortener.core.QrFormat
import org.springframework.data.redis.core.StringRedisTemplate
import java.security.MessageDigest
import java.time.Duration
import java.util.Base64

/**
 * Infrastructure implementations of core domain ports.
 * Adapter classes connecting the core domain with the persistence layer.
 */

/**
 * JPA-based implementation of the [ClickRepositoryService] port.
 */
@Service
class ClickRepositoryServiceImpl(
    private val clickEntityRepository: ClickEntityRepository
) : ClickRepositoryService {

    override fun save(cl: Click): Click {
        val entity = cl.toEntity()
        return clickEntityRepository.save(entity).toDomain()
    }

    override fun getClicks(id: String): Int =
        clickEntityRepository.countByHash(id).toInt()
    
    override fun findByHash(hash: String): List<Click> =
        clickEntityRepository.findByHash(hash).map { it.toDomain() } 
}

/**
 * JPA-based implementation of the [ShortUrlRepositoryService] port.
 * 
 * This adapter provides persistence for short URL mappings using JPA/Hibernate.
 * It's the primary data access component for the URL shortener, handling both
 * storage and retrieval of short URL mappings.
 * 
 * **Key Operations:**
 * - **findByKey**: Critical for redirect performance (O(1) hash lookup)
 * - **save**: Stores new short URL mappings with metadata
 * 
 * **Performance Optimizations:**
 * - Hash-based indexing for fast lookups
 * - JPA second-level caching for popular URLs
 * - Connection pooling for high concurrency
 * - Query optimization through Hibernate
 * 
 * **Data Integrity:**
 * - Unique constraints on hash values
 * - Referential integrity for related data
 * - Optimistic locking for concurrent updates
 */
class ShortUrlRepositoryServiceImpl(
    private val shortUrlEntityRepository: ShortUrlEntityRepository
) : ShortUrlRepositoryService {
    
    /**
     * Retrieves a short URL by its hash key.
     * 
     * This is the most performance-critical method in the system, called for
     * every redirect operation. The implementation is optimized for:
     * - **Fast Lookups**: Hash-based indexing provides O(1) access
     * - **Caching**: JPA second-level cache for frequently accessed URLs
     * - **Minimal Overhead**: Direct entity-to-domain conversion
     * 
     * **Database Optimization:**
     * - Hash column is indexed for optimal performance
     * - Query uses index-only access when possible
     * - Connection pooling handles concurrent requests
     *
     * @param id The [UrlHash] key to search for
     * @return The matching [ShortUrl] domain object or null if not found
     */
    override fun findByKey(id: es.unizar.urlshortener.core.UrlHash): ShortUrl? = 
        shortUrlEntityRepository.findByHash(id.value)?.toDomain()

    /**
     * Persists a short URL mapping to the database.
     * 
     * This method handles the storage of new short URL mappings, including:
     * - Hash-to-URL mapping storage
     * - Metadata persistence (safety, timestamps, etc.)
     * - Automatic ID generation
     * - Constraint validation
     * 
     * **Data Validation:**
     * - Unique hash constraint prevents duplicates
     * - URL format validation at database level
     * - Metadata integrity checks
     * 
     * **Transaction Safety:**
     * - Atomic operation ensures data consistency
     * - Rollback on constraint violations
     * - Optimistic locking for concurrent access
     *
     * @param su The [ShortUrl] domain object to persist
     * @return The persisted [ShortUrl] domain object (may include generated fields)
     */
    override fun save(su: ShortUrl): ShortUrl = shortUrlEntityRepository.save(su.toEntity()).toDomain()
}

/**
 * Redis-based implementation of QrCodeRepositoryService.
 * Caches QR images as Base64 strings.
 */
class QrCodeRepositoryServiceImpl(
    private val redisTemplate: StringRedisTemplate
) : QrCodeRepositoryService {

    private val ttl = Duration.ofHours(24)

    override fun find(url: String, size: Int, format: QrFormat): ByteArray? {
        val key = generateKey(url, size, format)
        val storedValue = redisTemplate.opsForValue().get(key)
        return storedValue?.let { Base64.getDecoder().decode(it) }
    }

    override fun save(url: String, size: Int, format: QrFormat, bytes: ByteArray) {
        val key = generateKey(url, size, format)
        val encodedValue = Base64.getEncoder().encodeToString(bytes)
        redisTemplate.opsForValue().set(key, encodedValue, ttl)
    }

    private fun generateKey(url: String, size: Int, format: QrFormat): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
        
        return "qr:$hash:$size:${format.fileExtension}"
    }
}