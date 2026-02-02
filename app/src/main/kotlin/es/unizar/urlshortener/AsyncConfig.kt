package es.unizar.urlshortener

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor

/**
 * Configuration for asynchronous processing.
 */
@Configuration
@EnableAsync
class AsyncConfig {
    /**
     * Task executor for asynchronous geolocation processing.
     */
    @Bean(name = ["geoTaskExecutor"])
    fun geoTaskExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 2
        executor.maxPoolSize = 5
        executor.queueCapacity = 100
        executor.setThreadNamePrefix("Geo-")
        executor.initialize()
        return executor
    }
}
