package es.unizar.urlshortener

import com.fasterxml.jackson.databind.ObjectMapper
import es.unizar.urlshortener.core.HashService
import es.unizar.urlshortener.core.QrCodeRepositoryService
import es.unizar.urlshortener.core.QrCodeService
import es.unizar.urlshortener.core.RateLimiterService
import es.unizar.urlshortener.core.ShortUrlRepositoryService
import es.unizar.urlshortener.core.UrlReachabilityService
import es.unizar.urlshortener.core.UrlSafeBrowsingService
import es.unizar.urlshortener.core.UrlValidationJobService
import es.unizar.urlshortener.core.UrlValidationWorkerService
import es.unizar.urlshortener.core.ValidatorService
import es.unizar.urlshortener.core.usecases.CreateShortUrlUseCaseImpl
import es.unizar.urlshortener.core.usecases.GenerateQrUseCase
import es.unizar.urlshortener.core.usecases.GenerateQrUseCaseImpl
import es.unizar.urlshortener.core.usecases.GetStatsUseCaseImpl
import es.unizar.urlshortener.core.usecases.LogClickUseCaseImpl
import es.unizar.urlshortener.core.usecases.RedirectUseCaseImpl
import es.unizar.urlshortener.infrastructure.delivery.HashServiceImpl
import es.unizar.urlshortener.infrastructure.delivery.QrCodeServiceImpl
import es.unizar.urlshortener.infrastructure.delivery.RateLimiterServiceImpl
import es.unizar.urlshortener.infrastructure.delivery.UrlReachabilityServiceImpl
import es.unizar.urlshortener.infrastructure.delivery.UrlSafeBrowsingServiceImpl
import es.unizar.urlshortener.infrastructure.delivery.UrlValidationJobServiceImpl
import es.unizar.urlshortener.infrastructure.delivery.UrlValidationWorkerServiceImpl
import es.unizar.urlshortener.infrastructure.delivery.ValidatorServiceImpl
import es.unizar.urlshortener.infrastructure.repositories.ClickEntityRepository
import es.unizar.urlshortener.infrastructure.repositories.ClickRepositoryServiceImpl
import es.unizar.urlshortener.infrastructure.repositories.QrCodeRepositoryServiceImpl
import es.unizar.urlshortener.infrastructure.repositories.ShortUrlEntityRepository
import es.unizar.urlshortener.infrastructure.repositories.ShortUrlRepositoryServiceImpl
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.StringRedisTemplate

/**
 * Wires use cases with service implementations, and services implementations with repositories.
 *
 * **Note**: Spring Boot is able to discover this [Configuration] without further configuration.
 * Uses modern constructor injection without @Autowired annotations.
 */
@Configuration
class ApplicationConfiguration(
    private val shortUrlEntityRepository: ShortUrlEntityRepository,
    private val clickEntityRepository: ClickEntityRepository,
) {
    /**
     * Provides an implementation of the ClickRepositoryService.
     * @return an instance of ClickRepositoryServiceImpl.
     */
    @Bean
    fun clickRepositoryService() = ClickRepositoryServiceImpl(clickEntityRepository)

    /**
     * Provides an implementation of the ShortUrlRepositoryService.
     * @return an instance of ShortUrlRepositoryServiceImpl.
     */
    @Bean
    fun shortUrlRepositoryService() = ShortUrlRepositoryServiceImpl(shortUrlEntityRepository)

    /**
     * Provides an implementation of the ValidatorService.
     * @return an instance of ValidatorServiceImpl.
     */
    @Bean
    fun validatorService() = ValidatorServiceImpl()

    /**
     * Provides an implementation of the HashService.
     * @return an instance of HashServiceImpl.
     */
    @Bean
    fun hashService() = HashServiceImpl()

    /**
     * Provides an implementation of the UrlReachabilityService.
     * @return an instance of UrlReachabilityServiceImpl.
     */
    @Bean
    fun urlReachabilityService(
        @Value("\${reachability.timeoutMillis:5000}") timeoutMillis: Long,
        @Value("\${reachability.cache.enabled:true}") cacheEnabled: Boolean,
        @Value("\${reachability.cache.ttl-minutes:10}") cacheTtlMinutes: Long,
        redisTemplate: StringRedisTemplate,
        objectMapper: ObjectMapper,
    ): UrlReachabilityService =
        UrlReachabilityServiceImpl(
            timeoutMillis,
            cacheEnabled,
            cacheTtlMinutes,
            redisTemplate,
            objectMapper,
        )

    /**
     * Provides an implementation of the QrCodeRepositoryService.
     */
    @Bean
    fun qrCodeRepositoryService(redisTemplate: StringRedisTemplate): QrCodeRepositoryService = QrCodeRepositoryServiceImpl(redisTemplate)

    /**
     * Provides an implementation of the QrCodeService.
     */
    @Bean
    fun qrCodeService(): QrCodeService = QrCodeServiceImpl()

    /**
     * Provides an implementation of the UrlValidationJobService.
     * This service acts as the orchestrator for async jobs, handling queueing (RabbitMQ)
     * and state management (Jobs Map/DB).
     * * @return an instance of UrlValidationJobServiceImpl.
     */
    @Bean
    fun urlValidationJobService(
        @Value("\${safebrowsing.rabbitQueue}") queueName: String,
        rabbitTemplate: RabbitTemplate,
        objectMapper: ObjectMapper,
        validatorService: ValidatorService,
    ): UrlValidationJobService = UrlValidationJobServiceImpl(queueName, rabbitTemplate, objectMapper, validatorService)

    /**
     * Provides an implementation of the UrlSafeBrowsingService.
     * This service is purely responsible for calling the Google Safe Browsing API.
     * * @return an instance of UrlSafeBrowsingServiceImpl.
     */
    @Bean
    fun urlSafeBrowsingService(
        @Value("\${safebrowsing.apiKey}") apiKey: String,
        @Value("\${safebrowsing.apiUrl}") apiUrl: String,
    ): UrlSafeBrowsingService = UrlSafeBrowsingServiceImpl(apiKey, apiUrl)

    /**
     * Provides an implementation of the RedirectUseCase.
     * @return an instance of RedirectUseCaseImpl.
     */
    @Bean
    fun redirectUseCase() = RedirectUseCaseImpl(shortUrlRepositoryService())

    /**
     * Provides an implementation of the RateLimiterService.
     * @return an instance of RateLimiterServiceImpl.
     */
    @Bean
    fun rateLimiterService(
        @Value("\${safebrowsing.ratelimit.perSecondCapacity}") perSecondCapacity: Long,
        @Value("\${safebrowsing.ratelimit.perSecondRefillTokens}") perSecondRefillTokens: Long,
        @Value("\${safebrowsing.ratelimit.perSecondRefillSeconds}") perSecondRefillSeconds: Long,
    ): RateLimiterService = RateLimiterServiceImpl(perSecondCapacity, perSecondRefillTokens, perSecondRefillSeconds)

    /**
     * Provides an implementation of the UrlValidationWorkerService.
     * This worker processes messages from the queue and coordinates the specific
     * validation steps (Reachability -> SafeBrowsing).
     * * @return an instance of UrlValidationWorkerServiceImpl.
     */
    @Bean
    @Suppress("LongParameterList")
    fun urlValidationWorkerService(
        urlValidationJobService: UrlValidationJobService,
        urlSafeBrowsingService: UrlSafeBrowsingService,
        urlReachabilityService: UrlReachabilityService,
        objectMapper: ObjectMapper,
        rateLimiterService: RateLimiterService,
        rabbitTemplate: RabbitTemplate,
        @Value("\${safebrowsing.rabbitQueue}") queueName: String,
        @Value("\${safebrowsing.resultQueue}") resultQueueName: String,
        @Value("\${resilience4j.retry.safebrowsing.max-attempts}") maxAttempts: Int,
        @Value("\${resilience4j.retry.safebrowsing.wait-duration}") waitDuration: String,
    ): UrlValidationWorkerService =
        UrlValidationWorkerServiceImpl(
            urlValidationJobService,
            urlSafeBrowsingService,
            urlReachabilityService,
            objectMapper,
            rateLimiterService,
            rabbitTemplate,
            queueName,
            resultQueueName,
            maxAttempts,
            waitDuration,
        )

    /**
     * Provides an implementation of the LogClickUseCase.
     * @return an instance of LogClickUseCaseImpl.
     */
    @Bean
    fun logClickUseCase() = LogClickUseCaseImpl(clickRepositoryService())

    /**
     * Provides an implementation of the GenerateQrUseCase.
     */
    @Bean
    fun generateQrUseCase(
        qrCodeService: QrCodeService,
        qrCodeRepositoryService: QrCodeRepositoryService,
    ): GenerateQrUseCase = GenerateQrUseCaseImpl(qrCodeService, qrCodeRepositoryService)

    /**
     * Provides an implementation of the CreateShortUrlUseCase.
     * @return an instance of CreateShortUrlUseCaseImpl.
     */
    @Bean
    fun createShortUrlUseCase(
        urlValidationJobService: UrlValidationJobService,
        shortUrlRepositoryService: ShortUrlRepositoryService,
        validatorService: ValidatorService,
        hashService: HashService,
    ) = CreateShortUrlUseCaseImpl(
        shortUrlRepositoryService,
        validatorService,
        hashService,
        urlValidationJobService,
    )

    /**
     * Provides an implementation of the GetStatsUseCase.
     * @return an instance of GetStatsUseCaseImpl.
     */
    @Bean
    fun getStatsUseCase() = GetStatsUseCaseImpl(clickRepositoryService())
}
