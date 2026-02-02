package es.unizar.urlshortener

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

/**
 * Configuration for geolocation HTTP client.
 */
@Configuration
class GeoClientConfig {
    @Value("\${geo.provider.base-url}")
    private lateinit var baseUrl: String

    @Value("\${geo.provider.timeout-ms}")
    private var timeoutMs: Long = 1500

    /**
     * WebClient for geolocation API calls with timeout configuration.
     */
    @Bean
    fun geoWebClient(): WebClient {
        return WebClient.builder()
            .baseUrl(baseUrl)
            .codecs { configurer ->
                configurer.defaultCodecs().maxInMemorySize(1024 * 1024)
            }
            .build()
    }
}
