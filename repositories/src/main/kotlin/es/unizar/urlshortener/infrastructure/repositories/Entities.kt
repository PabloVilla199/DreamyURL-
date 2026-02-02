@file:Suppress("WildcardImport")

package es.unizar.urlshortener.infrastructure.repositories

import jakarta.persistence.*
import java.time.OffsetDateTime

/**
 * JPA entities for the URL Shortener application persistence layer.
 * 
 * This file contains the **JPA entities** that represent the database schema
 * for the URL shortener application. These entities are part of the infrastructure
 * layer and handle the persistence concerns, while the domain layer uses
 * value objects and domain entities.
 * 
 * **Architecture Role:**
 * - **Persistence Models**: Represent database tables and relationships
 * - **Infrastructure Layer**: Handle database-specific concerns
 * - **Data Mapping**: Convert to/from domain objects via converters
 * - **JPA Integration**: Use JPA annotations for ORM mapping
 * 
 * **Design Principles:**
 * - **Separation of Concerns**: Persistence logic separate from domain logic
 * - **Database Optimization**: Optimized for database storage and queries
 * - **Type Safety**: Use appropriate data types for database columns
 * - **Nullable Fields**: Handle optional data appropriately
 * 
 * **Performance Considerations:**
 * - **Indexing**: Hash fields are indexed for fast lookups
 * - **Data Types**: Use appropriate column types for storage efficiency
 * - **Constraints**: Database-level constraints ensure data integrity
 * 
 * @see <a href="https://docs.oracle.com/javaee/7/tutorial/persistence-intro.htm">JPA Documentation</a>
 */

/**
 * JPA entity representing click tracking data in the database.
 * 
 * This entity stores analytics data for each click on a short URL, including
 * user information, timestamps, and metadata for business intelligence.
 * 
 * **Database Table**: `click`
 * **Primary Key**: `id` (auto-generated)
 * **Indexes**: `hash` (for fast lookups by short URL)
 * 
 * **Data Stored:**
 * - **id**: Unique identifier (auto-generated)
 * - **hash**: Short URL hash (for correlation with ShortUrlEntity)
 * - **created**: Timestamp when the click occurred
 * - **ip**: Client IP address (for geolocation and security)
 * - **referrer**: HTTP referrer header (traffic source)
 * - **browser**: User agent browser information
 * - **platform**: Operating system/platform information
 * - **country**: Geographic location (2-letter country code)
 * 
 * **Performance Features:**
 * - Auto-generated ID for efficient primary key
 * - Indexed hash field for fast lookups
 * - Optimized data types for storage efficiency
 * 
 * **Privacy Considerations:**
 * - IP addresses may be anonymized in production
 * - Personal data should be handled according to privacy regulations
 * - Consider data retention policies for analytics data
 */
@Entity
@Table(name = "click")
class ClickEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    val id: Long? = null,
    
    val hash: String,
    val created: OffsetDateTime,
    val ip: String?,
    val referrer: String?,
    val browser: String?,
    val platform: String?,
    val country: String?
)

/**
 * JPA entity representing short URL mappings in the database.
 * 
 * This entity stores the core data for the URL shortener: the mapping between
 * short URL hashes and their target URLs, along with metadata about the
 * short URL creation and properties.
 * 
 * **Database Table**: `shorturl`
 * **Primary Key**: `hash` (the short URL identifier)
 * **Indexes**: `hash` (primary key, automatically indexed)
 * 
 * **Data Stored:**
 * - **hash**: Short URL hash (primary key, unique identifier)
 * - **target**: The original long URL that gets redirected to
 * - **sponsor**: Optional sponsor/campaign information
 * - **created**: Timestamp when the short URL was created
 * - **owner**: Optional owner/creator information
 * - **mode**: HTTP redirect mode (301/307 status code)
 * - **safe**: Safety classification of the URL
 * - **ip**: Creator's IP address (for analytics)
 * - **country**: Creator's country (for analytics)
 * 
 * **Business Logic:**
 * - Hash serves as both primary key and short URL identifier
 * - Mode determines HTTP redirect behavior (permanent vs temporary)
 * - Safety flag indicates if URL has been classified as safe
 * - Timestamps enable analytics and cleanup operations
 * 
 * **Performance Optimizations:**
 * - Hash-based primary key for O(1) lookups
 * - Appropriate data types for storage efficiency
 * - Nullable fields for optional data
 * - Database constraints ensure data integrity
 * 
 * **Security Considerations:**
 * - Hash uniqueness prevents collisions
 * - Safety classification helps prevent malicious URLs
 * - IP tracking enables abuse detection
 */
@Entity
@Table(name = "shorturl")
@Suppress("LongParameterList")
class ShortUrlEntity(
    @Id
    val hash: String,
    val target: String,
    val sponsor: String?,
    val created: OffsetDateTime,
    val owner: String?,
    val mode: Int,
    val safe: Boolean,
    val ip: String?,
    val country: String?,
    
    val accessible: Boolean = false,
    
    @Column(name = "validated_at")
    val validatedAt: OffsetDateTime? = null,
    
    @Column(name = "validation_status_code")
    val validationStatusCode: Int? = null,
    
    @Column(name = "validation_response_time")
    val validationResponseTime: String? = null,
    
    @Column(name = "validation_content_type")
    val validationContentType: String? = null
)
