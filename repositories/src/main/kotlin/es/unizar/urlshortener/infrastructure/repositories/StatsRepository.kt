package es.unizar.urlshortener.infrastructure.repositories

/**
 * Aggregation repository backed by Redis for O(1) geolocation stats updates.
 */
interface StatsRepository {
    fun incrUrlTotal(shortUrlId: String)
    fun incrUrlCountry(shortUrlId: String, countryCode: String)
    fun incrUrlCity(shortUrlId: String, city: String, countryCode: String?)

    fun incrSystemTotal()
    fun incrSystemCountry(countryCode: String)
    fun incrSystemCity(city: String, countryCode: String?)

    fun getUrlAggregates(shortUrlId: String): UrlAggregates
    fun getSystemAggregates(): SystemAggregates
}

data class UrlAggregates(
    val total: Long,
    val countries: Map<String, Long>,
    val cities: Map<String, Long>
)

data class SystemAggregates(
    val total: Long,
    val countries: Map<String, Long>,
    val cities: Map<String, Long>
)
