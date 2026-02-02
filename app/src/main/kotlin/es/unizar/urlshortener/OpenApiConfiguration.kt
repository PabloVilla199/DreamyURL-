package es.unizar.urlshortener

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.servers.Server
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Configuration for OpenAPI/Swagger documentation.
 * Provides API documentation and interactive testing interface.
 *
 * Compatible with SpringDoc OpenAPI v2.8.13 and Spring Boot 3.5.x
 */
@Configuration
class OpenApiConfiguration {
    /**
     * Configures the OpenAPI specification for the URL Shortener API.
     * @return OpenAPI configuration with metadata and server information
     */
    @Bean
    fun openAPI(): OpenAPI {
        return OpenAPI()
            .info(
                Info()
                    .title("URL Shortener API")
                    .description(
                        """
                        A modern URL shortening service that transforms long URLs into short, 
                        shareable links. This API provides endpoints for creating short URLs 
                        and redirecting users to the original destinations.
                        
                        ## Features
                        - **URL Shortening**: Convert long URLs into short, manageable links
                        - **Click Tracking**: Monitor usage statistics for shortened URLs
                        - **Safe URLs**: Validate URLs for security and accessibility
                        - **RESTful Design**: Clean, intuitive API following REST principles
                        
                        ## Usage
                        1. Create a short URL by sending a POST request to `/api/link`
                        2. Use the returned short URL to redirect users
                        3. Access click statistics and analytics
                        
                        ## Security
                        All URLs are validated for safety and accessibility before shortening.
                        """.trimIndent(),
                    )
                    .version("1.0.0")
                    .contact(
                        Contact()
                            .name("URL Shortener Team")
                            .email("support@urlshortener.com")
                            .url("https://github.com/urlshortener"),
                    )
                    .license(
                        License()
                            .name("MIT License")
                            .url("https://opensource.org/licenses/MIT"),
                    ),
            )
            .servers(
                listOf(
                    Server()
                        .url("http://localhost:8080")
                        .description("Development server"),
                    Server()
                        .url("https://api.urlshortener.com")
                        .description("Production server"),
                ),
            )
    }
}
