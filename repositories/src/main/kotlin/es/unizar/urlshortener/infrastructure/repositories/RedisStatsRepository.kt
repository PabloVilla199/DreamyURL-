package es.unizar.urlshortener.infrastructure.repositories

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository

@Repository
open class RedisStatsRepository(
    private val redisTemplate: StringRedisTemplate
) : StatsRepository {

    private fun urlTotalKey(id: String) = "stats:url:$id:total"
    private fun urlCountriesKey(id: String) = "stats:url:$id:countries"
    private fun urlCitiesKey(id: String) = "stats:url:$id:cities"

    private val systemTotalKey = "stats:system:total"
    private val systemCountriesKey = "stats:system:countries"
    private val systemCitiesKey = "stats:system:cities"

    override fun incrUrlTotal(shortUrlId: String) {
        redisTemplate.opsForValue().increment(urlTotalKey(shortUrlId))
    }

    override fun incrUrlCountry(shortUrlId: String, countryCode: String) {
        redisTemplate.opsForHash<String, String>().increment(urlCountriesKey(shortUrlId), countryCode, 1)
    }

    override fun incrUrlCity(shortUrlId: String, city: String, countryCode: String?) {
        val field = cityKey(city, countryCode)
        redisTemplate.opsForHash<String, String>().increment(urlCitiesKey(shortUrlId), field, 1)
    }

    override fun incrSystemTotal() {
        redisTemplate.opsForValue().increment(systemTotalKey)
    }

    override fun incrSystemCountry(countryCode: String) {
        redisTemplate.opsForHash<String, String>().increment(systemCountriesKey, countryCode, 1)
    }

    override fun incrSystemCity(city: String, countryCode: String?) {
        val field = cityKey(city, countryCode)
        redisTemplate.opsForHash<String, String>().increment(systemCitiesKey, field, 1)
    }

    override fun getUrlAggregates(shortUrlId: String): UrlAggregates {
        val total = redisTemplate.opsForValue().get(urlTotalKey(shortUrlId))?.toLongOrNull() ?: 0L
        val countries = redisTemplate.opsForHash<String, String>().entries(urlCountriesKey(shortUrlId)).mapValues { it.value.toLong() }
        val cities = redisTemplate.opsForHash<String, String>().entries(urlCitiesKey(shortUrlId)).mapValues { it.value.toLong() }
        return UrlAggregates(total, countries, cities)
    }

    override fun getSystemAggregates(): SystemAggregates {
        val total = redisTemplate.opsForValue().get(systemTotalKey)?.toLongOrNull() ?: 0L
        val countries = redisTemplate.opsForHash<String, String>().entries(systemCountriesKey).mapValues { it.value.toLong() }
        val cities = redisTemplate.opsForHash<String, String>().entries(systemCitiesKey).mapValues { it.value.toLong() }
        return SystemAggregates(total, countries, cities)
    }

    private fun cityKey(city: String, countryCode: String?): String =
        if (countryCode.isNullOrBlank()) city else "$city|$countryCode"
}
