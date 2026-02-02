package es.unizar.urlshortener

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer

@Configuration
class RedisConfiguration {
    @Bean
    fun redisObjectMapper(): ObjectMapper =
        ObjectMapper().apply {
            registerKotlinModule()

            registerModule(JavaTimeModule())

            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }

    @Bean
    fun redisTemplate(
        connectionFactory: RedisConnectionFactory,
        redisObjectMapper: ObjectMapper,
    ): RedisTemplate<String, String> {
        val template = RedisTemplate<String, String>()
        template.connectionFactory = connectionFactory

        val stringSerializer = StringRedisSerializer()

        val jsonSerializer = Jackson2JsonRedisSerializer(redisObjectMapper, String::class.java)

        template.keySerializer = stringSerializer
        template.valueSerializer = jsonSerializer

        template.hashKeySerializer = stringSerializer
        template.hashValueSerializer = jsonSerializer

        template.afterPropertiesSet()
        return template
    }
}
