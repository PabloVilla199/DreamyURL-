import org.gradle.api.artifacts.VersionCatalogsExtension
plugins {
    // Applies the Kotlin JVM plugin to the project.
    kotlin("jvm")
    // Applies the Detekt plugin for static code analysis.
    id("io.gitlab.arturbosch.detekt")
}

kotlin {
    // Configures the Kotlin JVM toolchain to use JDK 17.
    jvmToolchain(17)
}

repositories {
    // Adds the Maven Central repository to the list of repositories.
    mavenCentral()
}

val catalogs = project.extensions.getByType<VersionCatalogsExtension>()
val libs = catalogs.named("libs")

dependencies {
    // Import Spring Boot BOM via platform for implementation and test scopes.
    val springBootVersion = libs.findVersion("springBoot").get().requiredVersion
    implementation(platform("org.springframework.boot:spring-boot-dependencies:${springBootVersion}"))
    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:${springBootVersion}"))

    // Common test dependencies for all modules.
    testImplementation(libs.findLibrary("kotlin-test").get())
    testImplementation(libs.findLibrary("mockito-kotlin").get())
    testImplementation(libs.findLibrary("junit-jupiter").get())
    testRuntimeOnly(libs.findLibrary("junit-platform-launcher").get())
}

tasks {
    test {
        // Configures the test task to use the JUnit Platform.
        useJUnitPlatform()
    }
}
