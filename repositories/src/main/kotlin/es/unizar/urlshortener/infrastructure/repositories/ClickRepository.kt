package es.unizar.urlshortener.infrastructure.repositories

import es.unizar.urlshortener.core.ClickInfo

/**
 * Repository for storing and retrieving enriched click information.
 */
interface ClickRepository {
    fun save(click: ClickInfo)
    fun findByShortUrlId(shortUrlId: String): List<ClickInfo>
}
