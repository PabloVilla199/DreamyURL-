package es.unizar.urlshortener.core

import java.time.Instant

/**
 * Servicio de geolocalización y procesamiento de clics.
 */
interface GeoService {
    /**
     * Procesa un clic enriqueciendo y almacenando su geolocalización mínima (país) para agregados rápidos.
     */
    fun processClick(event: ClickEvent)

    /**
     * Realiza una consulta de geolocalización detallada para una IP.
     * Debe aplicar: cache detalles -> proveedor primario -> proveedor fallback -> Unknown.
     */
    fun lookupIp(ip: String): GeoLookupResult
}

/**
 * Detalles de geolocalización
 */
data class GeoDetails(
    val countryCode: String?,
    val countryName: String?,
    val region: String?,
    val city: String?,
    val latitude: Double?,
    val longitude: Double?,
    val timezone: String?,
    val isp: String?,
    val organization: String?
) {
    companion object {
        val UNKNOWN = GeoDetails(
            countryCode = "XX",
            countryName = "Unknown",
            region = null,
            city = null,
            latitude = null,
            longitude = null,
            timezone = null,
            isp = null,
            organization = null
        )
    }
}

/**
 * Resultado de lookup
 */
data class GeoLookupResult(
    val ip: String,
    val geolocation: GeoDetails,
    val lookupMethod: LookupMethod,
    val lookupTimestamp: Instant = Instant.now()
)

enum class LookupMethod {
    CACHE, PRIMARY, FALLBACK, UNKNOWN
}

