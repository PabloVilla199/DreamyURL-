package es.unizar.urlshortener.infrastructure.delivery

import es.unizar.urlshortener.core.GeoService
import es.unizar.urlshortener.infrastructure.delivery.dto.*
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import java.net.URI
import java.time.format.DateTimeFormatter

@RestController
@RequestMapping("/api")
@Tag(
    name = "Geolocation Analytics",
    description = "Endpoints para obtener estadísticas de geolocalización de URLs y del sistema"
)
class GeolocationAnalyticsController(
    private val analyticsService: AnalyticsService,
    private val geoService: GeoService
) {
    @Operation(
        summary = "Obtener estadísticas de geolocalización por URL",
        description = "Retorna un resumen de países y ciudades desde donde se han accedido a una URL corta específica"
    )
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "Estadísticas encontradas",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = UrlGeolocationStatsResponse::class),
                examples = [ExampleObject(
                    name = "success",
                    value = """{
                        "url": "http://localhost:8080/abc123",
                        "geolocationStats": {
                            "totalClicks": 150,
                            "countries": [
                                {"name": "Spain", "code": "ES", "count": 90},
                                {"name": "France", "code": "FR", "count": 60}
                            ],
                            "cities": [
                                {"name": "Madrid", "count": 50},
                                {"name": "Barcelona", "count": 40}
                            ]
                        }
                    }"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "404",
            description = "No hay datos de geolocalización para esta URL",
            content = [Content(mediaType = "application/problem+json")]
        )
    ])
    @GetMapping("/link/{id}/geolocation")
    fun getUrlGeolocation(@PathVariable id: String): Any {
        val stats = analyticsService.getUrlStats(id)
        if (stats.totalClicks <= 0) return problemNotFound("/api/link/$id/geolocation")
        val url = ServletUriComponentsBuilder.fromCurrentContextPath().path("/{id}").buildAndExpand(id).toUriString()
        return UrlGeolocationStatsResponse(url = url, geolocationStats = stats)
    }

    @Operation(
        summary = "Obtener estadísticas de geolocalización del sistema",
        description = "Retorna un resumen agregado de países y ciudades de todos los accesos en el sistema"
    )
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "Estadísticas del sistema obtenidas",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = SystemGeolocationStatsResponse::class),
                examples = [ExampleObject(
                    name = "success",
                    value = """{
                        "systemGeolocationStats": {
                            "totalClicks": 5420,
                            "countries": [
                                {"name": "Spain", "code": "ES", "count": 2100},
                                {"name": "United States", "code": "US", "count": 1800}
                            ],
                            "cities": [
                                {"name": "Madrid", "count": 890},
                                {"name": "New York", "count": 650}
                            ]
                        }
                    }"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "404",
            description = "No hay datos de geolocalización en el sistema",
            content = [Content(mediaType = "application/problem+json")]
        )
    ])
    @GetMapping("/geolocation/system")
    fun getSystemGeolocation(): Any {
        val stats = analyticsService.getSystemStats()
        if (stats.totalClicks <= 0) return problemNotFound("/api/geolocation/system")
        return SystemGeolocationStatsResponse(systemGeolocationStats = stats)
    }

    @Operation(
        summary = "Hacer lookup de geolocalización para una IP",
        description = "Resuelve la ubicación geográfica de una dirección IP específica " +
            "usando los proveedores configurados"
    )
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "Información de geolocalización obtenida",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = GeoLookupResponse::class),
                examples = [ExampleObject(
                    name = "success",
                    value = """{
                        "ip": "8.8.8.8",
                        "geolocation": {
                            "country": "United States",
                            "countryCode": "US",
                            "region": "California",
                            "city": "Mountain View",
                            "latitude": 37.3861,
                            "longitude": -122.0839,
                            "timezone": "America/Los_Angeles",
                            "isp": "Google LLC",
                            "organization": "Google"
                        },
                        "lookupMethod": "primary",
                        "lookupTimestamp": "2025-12-04T10:45:30.123456Z"
                    }"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "400",
            description = "Dirección IP inválida",
            content = [Content(mediaType = "application/problem+json")]
        )
    ])
    @GetMapping("/geolocation/lookup")
    fun lookup(
        @Parameter(
            description = "Dirección IPv4 a resolver",
            example = "8.8.8.8",
            required = true
        )
        @RequestParam("ip") ip: String
    ): Any {
        if (!isValidIp(ip)) return problemBadRequest("/api/geolocation/lookup", "Invalid IP address")
        val result = geoService.lookupIp(ip)
        val details = result.geolocation
        val dto = GeoLookupResponse(
            ip = result.ip,
            geolocation = GeoLookupDetails(
                country = details.countryName,
                countryCode = details.countryCode,
                region = details.region,
                city = details.city,
                latitude = details.latitude,
                longitude = details.longitude,
                timezone = details.timezone,
                isp = details.isp,
                organization = details.organization
            ),
            lookupMethod = result.lookupMethod.name.lowercase(),
            lookupTimestamp = DateTimeFormatter.ISO_INSTANT.format(result.lookupTimestamp)
        )
        return dto
    }

    private fun problemNotFound(instance: String): ProblemDetail {
        val pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "No geolocation data available")
        pd.type = URI.create("https://api.urlshortener.unizar.es/problems/no-geolocation-data")
        pd.title = "No Geolocation Data"
        pd.instance = URI.create(instance)
        return pd
    }

    private fun problemBadRequest(instance: String, detail: String): ProblemDetail {
        val pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail)
        pd.type = URI.create("https://api.urlshortener.unizar.es/problems/invalid-ip")
        pd.title = "Invalid Input"
        pd.instance = URI.create(instance)
        return pd
    }

    private fun isValidIp(ip: String): Boolean {
        val ipv4Pattern = "^(25[0-5]|2[0-4]\\d|[0-1]?\\d{1,2})" +
            "(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d{1,2})){3}$"
        val regex = Regex(ipv4Pattern)
        return regex.matches(ip)
    }
}
