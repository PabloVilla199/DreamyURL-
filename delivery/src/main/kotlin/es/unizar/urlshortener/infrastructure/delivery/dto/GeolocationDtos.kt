package es.unizar.urlshortener.infrastructure.delivery.dto

data class GeolocationStats(
    val totalClicks: Long,
    val countries: List<CountryStats>,
    val cities: List<CityStats>
)

data class CountryStats(
    val country: String,
    val countryCode: String,
    val clicks: Long,
    val percentage: Double
)

data class CityStats(
    val city: String,
    val country: String,
    val clicks: Long,
    val percentage: Double
)

data class UrlGeolocationStatsResponse(
    val url: String,
    val geolocationStats: GeolocationStats
)

data class SystemGeolocationStatsResponse(
    val systemGeolocationStats: GeolocationStats
)

data class GeoLookupResponse(
    val ip: String,
    val geolocation: GeoLookupDetails,
    val lookupMethod: String,
    val lookupTimestamp: String
)

data class GeoLookupDetails(
    val country: String?,
    val countryCode: String?,
    val region: String?,
    val city: String?,
    val latitude: Double?,
    val longitude: Double?,
    val timezone: String?,
    val isp: String?,
    val organization: String?
)
