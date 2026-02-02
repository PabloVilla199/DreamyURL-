package es.unizar.urlshortener.infrastructure.repositories

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
/**
 * Spring Data JPA repositories for the URL Shortener application.
 * 
 * This file contains the **Spring Data JPA repository interfaces** that provide
 * data access methods for the JPA entities. These repositories are part of the
 * infrastructure layer and implement the data access patterns using Spring Data JPA.
 * 
 * **Architecture Role:**
 * - **Data Access Layer**: Provide CRUD operations for JPA entities
 * - **Spring Data Integration**: Leverage Spring Data JPA for automatic implementation
 * - **Query Methods**: Define custom query methods using Spring Data conventions
 * - **Infrastructure Layer**: Handle database-specific data access concerns
 * 
 * **Spring Data JPA Features:**
 * - **Automatic Implementation**: Spring generates implementations at runtime
 * - **Query Methods**: Method names automatically generate SQL queries
 * - **CRUD Operations**: Inherit standard CRUD operations from JpaRepository
 * - **Transaction Management**: Automatic transaction handling
 * 
 * **Performance Benefits:**
 * - **Optimized Queries**: Spring Data generates efficient SQL
 * - **Connection Pooling**: Managed by Spring Boot
 * - **Caching**: Second-level cache support
 * - **Batch Operations**: Efficient bulk operations
 * 
 * @see <a href="https://docs.spring.io/spring-data/jpa/docs/current/reference/html/">Spring Data JPA Documentation</a>
 */

/**
 * Spring Data JPA repository for [ShortUrlEntity] operations.
 * 
 * This repository provides data access methods for short URL entities, including
 * the critical hash-based lookup operation that is the most performance-sensitive
 * part of the application.
 * 
 * **Key Operations:**
 * - **findByHash**: Critical for redirect performance (O(1) hash lookup)
 * - **save**: Store new short URL mappings
 * - **findAll**: Retrieve all short URLs (for administration)
 * - **delete**: Remove short URL mappings
 * 
 * **Performance Characteristics:**
 * - **Hash Lookup**: Optimized for fast hash-based queries
 * - **Index Usage**: Leverages database indexes for optimal performance
 * - **Connection Pooling**: Managed by Spring Boot for scalability
 * - **Query Optimization**: Spring Data generates efficient SQL
 * 
 * **Spring Boot Integration:**
 * - **Auto-discovery**: Automatically registered as a Spring bean
 * - **Configuration**: No additional configuration required
 * - **Transaction Management**: Automatic transaction handling
 * - **Error Handling**: Spring Data exception translation
 * 
 * **Custom Query Methods:**
 * - `findByHash`: Custom method for hash-based lookups
 * - Method name follows Spring Data conventions
 * - Automatically generates optimized SQL queries
 * - Returns null for non-existent hashes
 */
interface ShortUrlEntityRepository : JpaRepository<ShortUrlEntity, String> {
    /**
     * Retrieves a short URL entity by its hash key.
     * 
     * This is the most performance-critical method in the system, called for
     * every redirect operation. The implementation is optimized for:
     * - **Fast Lookups**: Hash-based indexing provides O(1) access
     * - **Database Optimization**: Uses primary key index for optimal performance
     * - **Null Safety**: Returns null for non-existent hashes
     * 
     * **Generated SQL**: `SELECT * FROM shorturl WHERE hash = ?`
     * **Performance**: Uses primary key index for optimal query execution
     * **Concurrency**: Thread-safe operation with proper transaction handling
     *
     * @param hash The short URL hash to search for
     * @return The matching [ShortUrlEntity] or null if not found
     */
    fun findByHash(hash: String): ShortUrlEntity?
}

/**
 * Spring Data JPA repository for [ClickEntity] operations.
 * 
 * This repository provides data access methods for click tracking entities,
 * handling the storage and retrieval of analytics data for the URL shortener.
 * 
 * **Key Operations:**
 * - **save**: Store click tracking data (most frequently used)
 * - **findAll**: Retrieve all clicks (for analytics)
 * - **findById**: Retrieve specific click by ID
 * - **delete**: Remove click records (for data cleanup)
 * 
 * **Analytics Features:**
 * - **High Volume**: Designed for high-frequency click logging
 * - **Batch Operations**: Efficient bulk insert operations
 * - **Query Flexibility**: Support for complex analytics queries
 * - **Data Retention**: Support for data cleanup operations
 * 
 * **Performance Considerations:**
 * - **Write Optimization**: Optimized for frequent insertions
 * - **Index Strategy**: Hash field indexed for correlation queries
 * - **Connection Pooling**: Managed by Spring Boot for scalability
 * - **Transaction Management**: Automatic transaction handling
 * 
 * **Spring Boot Integration:**
 * - **Auto-discovery**: Automatically registered as a Spring bean
 * - **Configuration**: No additional configuration required
 * - **Exception Handling**: Spring Data exception translation
 * - **Auditing**: Support for entity auditing if needed
 * 
 * **Data Management:**
 * - **Primary Key**: Auto-generated Long ID for efficient storage
 * - **Foreign Key**: Hash field correlates with ShortUrlEntity
 * - **Timestamps**: Automatic timestamp management
 * - **Nullable Fields**: Proper handling of optional analytics data
 */
interface ClickEntityRepository : JpaRepository<ClickEntity, Long> {
    fun findByHash(hash: String): List<ClickEntity>  
    fun countByHash(hash: String): Long
    
    // Queries opcionales para estadísticas
    @Query("SELECT c.browser, COUNT(c) FROM ClickEntity c WHERE c.hash = :hash AND c.browser IS NOT NULL GROUP BY c.browser")
    fun getBrowserStats(hash: String): List<Array<Any>>
    
    @Query("SELECT c.platform, COUNT(c) FROM ClickEntity c WHERE c.hash = :hash AND c.platform IS NOT NULL GROUP BY c.platform")
    fun getPlatformStats(hash: String): List<Array<Any>>
    
    @Query("SELECT c.country, COUNT(c) FROM ClickEntity c WHERE c.hash = :hash AND c.country IS NOT NULL GROUP BY c.country")
    fun getCountryStats(hash: String): List<Array<Any>>
}
