package es.unizar.urlshortener.infrastructure.delivery

import es.unizar.urlshortener.infrastructure.repositories.StatsRepository
import es.unizar.urlshortener.infrastructure.repositories.SystemAggregates
import es.unizar.urlshortener.infrastructure.repositories.UrlAggregates
import es.unizar.urlshortener.infrastructure.delivery.dto.*
import org.springframework.stereotype.Service
import java.util.Locale

@Service
class AnalyticsService(
    private val statsRepository: StatsRepository
) {
    fun getUrlStats(shortUrlId: String): GeolocationStats {
        val agg: UrlAggregates = statsRepository.getUrlAggregates(shortUrlId)
        return toStats(agg.total, agg.countries, agg.cities)
    }

    fun getSystemStats(): GeolocationStats {
        val agg: SystemAggregates = statsRepository.getSystemAggregates()
        return toStats(agg.total, agg.countries, agg.cities)
    }

    private fun toStats(total: Long, countriesMap: Map<String, Long>, citiesMap: Map<String, Long>): GeolocationStats {
        val totalClicks = total
        val countries = countriesMap.entries
            .sortedByDescending { it.value }
            .map { (code, clicks) ->
                val name = countryName(code)
                CountryStats(
                    country = name,
                    countryCode = code,
                    clicks = clicks,
                    percentage = percentage(clicks, totalClicks)
                )
            }
        val cities = citiesMap.entries
            .sortedByDescending { it.value }
            .map { (field, clicks) ->
                val (city, code) = splitCityField(field)
                CityStats(
                    city = city,
                    country = countryName(code),
                    clicks = clicks,
                    percentage = percentage(clicks, totalClicks)
                )
            }
        return GeolocationStats(totalClicks, countries, cities)
    }

    private fun percentage(part: Long, total: Long): Double {
        if (total <= 0) return 0.0
        return ((part.toDouble() / total.toDouble()) * 1000.0).toInt() / 10.0
    }

    private fun countryName(code: String?): String = when {
        code.isNullOrBlank() -> "Unknown"
        code.equals("XX", ignoreCase = true) -> "Unknown"
        else -> Locale("", code.uppercase()).displayCountry.ifBlank { code.uppercase() }
    }

    private fun splitCityField(field: String): Pair<String, String?> {
        val idx = field.lastIndexOf('|')
        return if (idx > 0) field.substring(0, idx) to field.substring(idx + 1) else field to null
    }
}
