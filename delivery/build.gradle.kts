plugins {
    // Apply the common conventions plugin for the project
    id("urlshortener-common-conventions")

    // Apply the Kotlin JPA plugin
    alias(libs.plugins.kotlin.jpa)

    // Apply the Kotlin Spring plugin
    alias(libs.plugins.kotlin.spring)

    // Apply the Spring Boot plugin but do not apply it immediately
    alias(libs.plugins.spring.boot) apply false

    id("jacoco-conventions")
}

dependencies {
    // Include the core project as an implementation dependency
    implementation(project(":core"))

    // Add Kotlin Logging for idiomatic Kotlin logging
    implementation(libs.kotlin.logging)

    // Include Spring Boot Starter Web as an implementation dependency
    implementation(libs.spring.boot.starter.web)

    // Include Spring Boot Starter HATEOAS as an implementation dependency
    implementation(libs.spring.boot.starter.hateoas)

    // Include Apache Commons Validator as an implementation dependency
    implementation(libs.commons.validator)

    // Include Google Guava as an implementation dependency
    implementation(libs.guava)

    // Include OpenAPI/Swagger documentation as an implementation dependency
    implementation(libs.springdoc.openapi)

    implementation("com.google.zxing:core:3.5.0")
    implementation("com.google.zxing:javase:3.5.0")

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.kotlin.test)
    
    testImplementation(libs.jackson.module.kotlin)
    
    // Para JavaTimeModule en tests
    testImplementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.0")

    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation(project(":repositories"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("eu.bitwalker:UserAgentUtils:1.21")

       // RabbitMQ
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    // Bucket4j para rate limiting
    implementation("com.bucket4j:bucket4j-core:8.7.0")

    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")
    implementation("io.github.resilience4j:resilience4j-kotlin:2.2.0")



     implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
}
