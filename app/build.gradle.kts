plugins {
    // Applies the common conventions plugin for the URL shortener project.
    id("urlshortener-common-conventions")

    id("org.jlleitschuh.gradle.ktlint") version "12.1.0"

    // Applies the Kotlin Spring plugin using an alias from the version catalog.
    alias(libs.plugins.kotlin.spring)
    // Applies the Spring Boot plugin using an alias from the version catalog.
    alias(libs.plugins.spring.boot)

    id("jacoco-conventions")
}

dependencies {
    // Adds the core project as an implementation dependency.
    implementation(project(":core"))
    // Adds the delivery project as an implementation dependency.
    implementation(project(":delivery"))
    // Adds the repositories project as an implementation dependency.
    implementation(project(":repositories"))

    // Adds the Spring Boot starter as an implementation dependency.
    implementation(libs.spring.boot.starter)
    // Adds Spring Boot Actuator for monitoring and management endpoints.
    implementation(libs.spring.boot.starter.actuator)
    // Adds Bootstrap as an implementation dependency.
    implementation(libs.bootstrap)
    // Adds OpenAPI/Swagger documentation as an implementation dependency.
    implementation(libs.springdoc.openapi)
    // Adds Spring Boot WebFlux for reactive WebClient support (needed by GeoClientConfig)
    implementation(libs.spring.boot.starter.webflux)

    // Adds HSQLDB as a runtime-only dependency.
    runtimeOnly(libs.hsqldb)
    // Adds Kotlin reflection library as a runtime-only dependency.
    runtimeOnly(libs.kotlin.reflect)

    // Adds Spring Boot starter test library as a test implementation dependency.
    testImplementation(libs.spring.boot.starter.test)
    // Adds Spring Boot starter web library as a test implementation dependency.
    testImplementation(libs.spring.boot.starter.web)
    // Adds Spring Boot starter JDBC library as a test implementation dependency.
    testImplementation(libs.spring.boot.starter.jdbc)
    // Adds Apache HttpClient 5 as a test implementation dependency.
    testImplementation(libs.httpclient5)

    implementation("me.paulschwarz:spring-dotenv:4.0.0")
    implementation(libs.spring.boot.starter.data.redis)
    implementation(libs.spring.boot.starter.webflux)

    implementation(libs.jackson.module.kotlin)

    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.2")

    // Bucket4j rate limiter (shared with delivery module)
    implementation("com.bucket4j:bucket4j-core:8.7.0")

    testImplementation("org.testcontainers:testcontainers:1.19.3")
    testImplementation("org.testcontainers:junit-jupiter:1.19.3")
    // RabbitMQ support for messaging
    implementation("org.springframework.boot:spring-boot-starter-amqp")

    // Jackson Kotlin support for message serialization
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
}
