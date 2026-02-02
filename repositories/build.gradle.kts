plugins {
    // Apply the common conventions plugin for the URL shortener project
    id("urlshortener-common-conventions")

    // Apply the Kotlin JPA plugin
    alias(libs.plugins.kotlin.jpa)

    // Apply the Spring Boot plugin without automatically applying it
    alias(libs.plugins.spring.boot) apply false

    id("jacoco-conventions")
}

dependencies {
    // Add the core project as an implementation dependency
    implementation(project(":core"))

    // Add the Spring Boot Starter Data JPA library as an implementation dependency
    implementation(libs.spring.boot.starter.data.jpa)


    // Add dependecys for Google Safe Browsing API
    implementation("com.google.apis:google-api-services-safebrowsing:v4-rev134-1.25.0")
    implementation("com.google.api-client:google-api-client:2.2.0")
    implementation("com.google.oauth-client:google-oauth-client-jetty:1.34.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    // Add the Spring Boot Starter Web library as an implementation dependency
    implementation(libs.spring.boot.starter.data.redis)

    // Added dependencies
    implementation(libs.jackson.module.kotlin)

    // Soporte para serialización de fechas (Instant) en tests
    testImplementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.0")
}

