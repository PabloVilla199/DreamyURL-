@file:Suppress("MaxLineLength")

package es.unizar.urlshortener

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Spring Boot application entry point for the URL Shortener service.
 *
 * This class serves as the main application class that bootstraps the Spring Boot
 * application. It uses the `@SpringBootApplication` annotation which combines:
 * - `@Configuration`: Marks this class as a source of bean definitions
 * - `@EnableAutoConfiguration`: Enables Spring Boot's auto-configuration
 * - `@ComponentScan`: Enables component scanning for this package and sub-packages
 *
 * **Application Architecture:**
 * - **Multi-module Structure**: Scans all modules (core, delivery, repositories, app)
 * - **Auto-configuration**: Automatically configures Spring Boot components
 * - **Dependency Injection**: Wires all components using Spring's DI container
 * - **Embedded Server**: Starts embedded Tomcat server for web requests
 *
 * **Module Integration:**
 * - **Core Module**: Domain logic and use cases
 * - **Delivery Module**: REST controllers and web layer
 * - **Repositories Module**: Data persistence layer
 * - **App Module**: Configuration and static resources
 *
 * **Spring Boot Features:**
 * - **Auto-configuration**: Automatically configures JPA, web, and other components
 * - **Embedded Server**: Runs on embedded Tomcat (default port 8080)
 * - **Health Checks**: Built-in health check endpoints via Actuator
 * - **Actuator**: Production-ready monitoring and management features
 *
 * **Development vs Production:**
 * - **Development**: Uses HSQLDB in-memory database
 * - **Production**: Can be configured for external databases
 * - **Profiles**: Supports different configurations via Spring profiles
 *
 * @see <a href="https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#using-boot-using-springbootapplication-annotation">Spring Boot Application Annotation</a>
 */
@SpringBootApplication
class Application

/**
 * Main entry point for the URL Shortener application.
 *
 * This function starts the Spring Boot application with the specified command line
 * arguments. It uses the `runApplication` function which:
 * - Creates and configures the Spring ApplicationContext
 * - Starts the embedded web server
 * - Registers all Spring beans and components
 * - Enables auto-configuration for all modules
 *
 * **Command Line Arguments:**
 * - Standard Spring Boot arguments (--server.port, --spring.profiles.active, etc.)
 * - Custom application properties
 * - JVM arguments for memory and performance tuning
 *
 * **Application Startup:**
 * 1. **Context Initialization**: Creates Spring ApplicationContext
 * 2. **Auto-configuration**: Configures JPA, web, and other components
 * 3. **Bean Registration**: Registers all components from all modules
 * 4. **Database Initialization**: Sets up HSQLDB in-memory database
 * 5. **Web Server Startup**: Starts embedded Tomcat server
 * 6. **Ready State**: Application is ready to handle requests
 *
 * **Access Points:**
 * - **Web Interface**: http://localhost:8080/
 * - **REST API**: http://localhost:8080/api/link
 * - **API Documentation**: http://localhost:8080/swagger-ui.html
 * - **Health Check**: http://localhost:8080/actuator/health (detailed health status)
 * - **Application Info**: http://localhost:8080/actuator/info (application metadata)
 * - **Actuator Discovery**: http://localhost:8080/actuator (available endpoints)
 *
 * @param args Command line arguments passed to the application
 */
fun main(args: Array<String>) {
    @Suppress("SpreadOperator")
    runApplication<Application>(*args)
}
