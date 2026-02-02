package es.unizar.urlshortener.infrastructure.geolocation

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import es.unizar.urlshortener.core.ClickEvent
import es.unizar.urlshortener.core.ClickInfo
import es.unizar.urlshortener.core.GeoDetails
import es.unizar.urlshortener.core.GeoLookupResult
import es.unizar.urlshortener.core.GeoService
import es.unizar.urlshortener.core.LookupMethod
import es.unizar.urlshortener.infrastructure.repositories.ClickRepository
import es.unizar.urlshortener.infrastructure.repositories.StatsRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration
import java.time.temporal.ChronoUnit

/**
 * Implementation of GeoService for resolving geolocation and storing enriched clicks.
 */
@Service
class GeoServiceImpl(
    private val webClient: WebClient,
    private val redisTemplate: StringRedisTemplate,
    private val clickRepository: ClickRepository,
    private val statsRepository: StatsRepository,
    @param:Value("\${geo.provider.base-url}") private val primaryBaseUrl: String,
    @param:Value("\${geo.provider.path}") private val providerPath: String,
    @param:Value("\${geo.provider.api-key}") private val apiKey: String,
    @param:Value("\${geo.provider.timeout-ms}") private val timeoutMs: Long,
    @param:Value("\${geo.cache-ttl-days}") private val cacheTtlDays: Long,
    @param:Value("\${geo.unknown-ttl-minutes:60}") private val unknownTtlMinutes: Long,
    @param:Value("\${geo.fallback.base-url:http://ip-api.com}") private val fallbackBaseUrl: String,
    @param:Value("\${geo.fallback.path:/json/{ip}}") private val fallbackPath: String,
) : GeoService {
    private val logger = LoggerFactory.getLogger(GeoServiceImpl::class.java)
    private val objectMapper = jacksonObjectMapper()
    private val legacyPrefix = "geo:"
    private val detailsPrefix = "geo:details:"

    override fun processClick(event: ClickEvent) {
        val ip = event.ip // Store in local variable to avoid smart cast issues

        // Resolver detalles una sola vez si procede (evita llamadas duplicadas al proveedor y sets en Redis)
        val detailsResult: GeoLookupResult =
            if (ip.isNullOrBlank() || isPrivateIp(ip)) {
                // No hacemos lookup de proveedor para IPs vacías/privadas
                GeoLookupResult(ip ?: "", GeoDetails.UNKNOWN, LookupMethod.UNKNOWN)
            } else {
                resolveDetails(ip)
            }

        // Obtener country para el ClickInfo — normalizamos código sentinel "XX" y valores vacíos a "Unknown"
        val rawCountry = detailsResult.geolocation.countryCode
        val countryForClick = rawCountry?.takeIf { it.isNotBlank() && it != "XX" } ?: "Unknown"

        val clickInfo =
            ClickInfo(
                event.shortUrlId,
                ip,
                countryForClick,
                event.referrer,
                event.browser,
                event.platform,
                event.timestamp,
            )

        clickRepository.save(clickInfo)
        logger.info("Saved click for shortUrlId: ${event.shortUrlId}, country: $countryForClick")

        statsRepository.incrUrlTotal(event.shortUrlId)
        statsRepository.incrSystemTotal()

        // Solo hacer lookup de detalles si necesitamos analytics más detallados
        if (!ip.isNullOrBlank() && !isPrivateIp(ip)) {
            // Reutilizamos detailsResult en lugar de volver a llamar resolveDetails/lookupIp
            detailsResult.geolocation.countryCode?.let { cc ->
                statsRepository.incrUrlCountry(event.shortUrlId, cc)
                statsRepository.incrSystemCountry(cc)
            }
            val city = detailsResult.geolocation.city
            if (city != null) {
                statsRepository.incrUrlCity(event.shortUrlId, city, detailsResult.geolocation.countryCode)
                statsRepository.incrSystemCity(city, detailsResult.geolocation.countryCode)
            }
        }
    }

    override fun lookupIp(ip: String): GeoLookupResult = resolveDetails(ip)

    private fun isPrivateIp(ip: String): Boolean {
        return ip.startsWith("10.") ||
            ip.startsWith("192.168.") ||
            ip.startsWith("127.") ||
            (ip.startsWith("172.") && ip.split('.').getOrNull(1)?.toIntOrNull()?.let { it in 16..31 } == true)
    }

    private fun resolveDetails(ip: String): GeoLookupResult {
        if (ip.isBlank() || isPrivateIp(ip)) {
            cacheUnknown(ip)
            return GeoLookupResult(ip, GeoDetails.UNKNOWN, LookupMethod.UNKNOWN)
        }

        // Primero verificar caché de detalles
        val detailsKey = "$detailsPrefix$ip"
        redisTemplate.opsForValue().get(detailsKey)?.let { json ->
            try {
                val details: GeoDetails = objectMapper.readValue(json)
                return GeoLookupResult(ip, details, LookupMethod.CACHE)
            } catch (ex: Exception) {
                logger.warn("Corrupted details cache for $ip. Ignoring.", ex)
            }
        }

        // Si no hay detalles, verificar caché legacy
        val legacyKey = "$legacyPrefix$ip"
        redisTemplate.opsForValue().get(legacyKey)?.let { cc ->
            if (cc != "Unknown") {
                val details =
                    GeoDetails(
                        countryCode = cc,
                        countryName = null,
                        region = null,
                        city = null,
                        latitude = null,
                        longitude = null,
                        timezone = null,
                        isp = null,
                        organization = null,
                    )
                return GeoLookupResult(ip, details, LookupMethod.CACHE)
            }
        }

        // Si no hay caché, consultar proveedores
        val primary = fetchFromPrimary(ip)
        if (primary != null) {
            cacheDetails(ip, primary)
            return GeoLookupResult(ip, primary, LookupMethod.PRIMARY)
        }

        val fallback = fetchFromFallback(ip)
        if (fallback != null) {
            cacheDetails(ip, fallback)
            return GeoLookupResult(ip, fallback, LookupMethod.FALLBACK)
        }

        cacheUnknown(ip)
        return GeoLookupResult(ip, GeoDetails.UNKNOWN, LookupMethod.UNKNOWN)
    }

    private fun resolveCountry(ipAddress: String?): String {
        if (ipAddress.isNullOrBlank()) return "Unknown"

        val legacyKey = "$legacyPrefix$ipAddress"
        redisTemplate.opsForValue().get(legacyKey)?.let { cached ->
            if (cached != "Unknown") return cached
        }

        // Para country, usamos resolveDetails que ya maneja el caching
        val detailsResult =
            try {
                resolveDetails(ipAddress)
            } catch (ex: Exception) {
                logger.warn("Failed to resolve details for country fallback for IP: $ipAddress", ex)
                GeoLookupResult(ipAddress, GeoDetails.UNKNOWN, LookupMethod.UNKNOWN)
            }

        return detailsResult.geolocation.countryCode ?: "Unknown"
    }

    private fun cacheDetails(
        ip: String,
        details: GeoDetails,
    ) {
        val detailsKey = "$detailsPrefix$ip"
        val json = objectMapper.writeValueAsString(details)
        redisTemplate.opsForValue().set(detailsKey, json, Duration.of(cacheTtlDays, ChronoUnit.DAYS))

        // También cachear el country code en legacy para resolveCountry
        val countryCode = details.countryCode ?: "Unknown"
        val legacyKey = "$legacyPrefix$ip"
        redisTemplate.opsForValue().set(legacyKey, countryCode, Duration.of(cacheTtlDays, ChronoUnit.DAYS))
    }

    private fun cacheUnknown(ip: String) {
        if (ip.isBlank()) return
        val legacyKey = "$legacyPrefix$ip"
        val detailsKey = "$detailsPrefix$ip"
        val ttl = Duration.ofMinutes(unknownTtlMinutes) // única instancia reutilizada
        redisTemplate.opsForValue().set(legacyKey, "Unknown", ttl)
        redisTemplate.opsForValue().set(detailsKey, objectMapper.writeValueAsString(GeoDetails.UNKNOWN), ttl)
    }

    private fun fetchFromPrimary(ip: String): GeoDetails? {
        val url =
            buildString {
                append(primaryBaseUrl.trimEnd('/'))
                val path = providerPath.replace("{ip}", ip)
                if (!path.startsWith('/')) append('/')
                append(path)
            }
        val request = webClient.get().uri(url)
        val finalRequest = if (apiKey.isNotBlank()) request.header("Authorization", "Bearer $apiKey") else request
        return try {
            finalRequest
                .retrieve()
                .bodyToMono(IpApiCoResponse::class.java)
                .timeout(Duration.ofMillis(timeoutMs))
                .map { it.toGeoDetails() }
                .block()
        } catch (ex: Exception) {
            logger.warn("Primary provider failed for $ip", ex)
            null
        }
    }

    private fun fetchFromFallback(ip: String): GeoDetails? {
        val path = fallbackPath.replace("{ip}", ip)
        val url =
            buildString {
                append(fallbackBaseUrl.trimEnd('/'))
                if (!path.startsWith('/')) append('/')
                append(path)
            }
        return try {
            webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(IpApiComResponse::class.java)
                .timeout(Duration.ofMillis(timeoutMs))
                .map { it.toGeoDetails() }
                .block()
        } catch (ex: Exception) {
            logger.warn("Fallback provider failed for $ip", ex)
            null
        }
    }

    /** Response model for ipapi.co */
    data class IpApiCoResponse(
        @param:JsonProperty("country") val countryCode: String? = null,
        @param:JsonProperty("country_name") val countryName: String? = null,
        @param:JsonProperty("region") val region: String? = null,
        @param:JsonProperty("city") val city: String? = null,
        @param:JsonProperty("latitude") val latitude: Double? = null,
        @param:JsonProperty("longitude") val longitude: Double? = null,
        @param:JsonProperty("timezone") val timezone: String? = null,
        @param:JsonProperty("org") val organization: String? = null,
        @param:JsonProperty("isp") val isp: String? = null,
    ) {
        fun toGeoDetails() =
            GeoDetails(
                countryCode = countryCode,
                countryName = countryName,
                region = region,
                city = city,
                latitude = latitude,
                longitude = longitude,
                timezone = timezone,
                isp = isp ?: organization,
                organization = organization,
            )
    }

    /** Response model for ip-api.com */
    data class IpApiComResponse(
        @param:JsonProperty("countryCode") val countryCode: String? = null,
        @param:JsonProperty("country") val countryName: String? = null,
        @param:JsonProperty("regionName") val region: String? = null,
        @param:JsonProperty("city") val city: String? = null,
        @param:JsonProperty("lat") val latitude: Double? = null,
        @param:JsonProperty("lon") val longitude: Double? = null,
        @param:JsonProperty("timezone") val timezone: String? = null,
        @param:JsonProperty("isp") val isp: String? = null,
        @param:JsonProperty("org") val organization: String? = null,
        @param:JsonProperty("status") val status: String? = null,
    ) {
        fun toGeoDetails() =
            GeoDetails(
                countryCode = countryCode,
                countryName = countryName,
                region = region,
                city = city,
                latitude = latitude,
                longitude = longitude,
                timezone = timezone,
                isp = isp,
                organization = organization,
            )
    }
}
