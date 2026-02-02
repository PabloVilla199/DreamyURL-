# DreamyURL - Advanced URL Shortener

2025

[![](https://github.com/Marcos1236/DreamyURL/actions/workflows/ci.yml/badge.svg)](https://github.com/Marcos1236/DreamyURL/actions/workflows/ci.yml/badge.svg)

## 🎯 Learning Objectives

This project demonstrates modern web engineering practices and serves as an educational example for:

- **Clean Architecture** implementation with clear separation of concerns (Hexagonal Architecture).
- **Asynchronous Processing** using Message Brokers (RabbitMQ).
- **High-Performance Caching** and Atomic counting using Redis.
- **Resilience Patterns** (Retry, Rate Limiting) using Resilience4j and Bucket4j.
- **Modern Kotlin** features including value classes, sealed classes, and coroutines.
- **Spring Boot** best practices and dependency injection.
- **RESTful API** design with OpenAPI documentation.

## 🛠️ Technology Stack

This application showcases modern web engineering technologies and best practices:

### Core Technologies

1. **Programming Language**: [Kotlin 2.2.10](https://kotlinlang.org/)
2. **Build System**: [Gradle 9.0](https://gradle.org/)
3. **Framework**: [Spring Boot 3.5.4](https://docs.spring.io/spring-boot/)

### Infrastructure & Libraries

- **Messaging**: RabbitMQ (for async URL validation).
- **Caching & Stats**: Redis (for caching QR/Geo data and atomic counters).
- **Database**: HSQLDB (Persistence) / Spring Data JPA.
- **Resilience**: Resilience4j (Retries), Bucket4j (Rate Limiting).
- **API Documentation**: OpenAPI 3.0 (SpringDoc).
- **Utilities**: Google Safe Browsing API, ZXing (QR Codes), UserAgentUtils.
- **Testing**: JUnit 5, Mockito, TestContainers.

## 🚀 Getting Started & Configuration

Follow these steps to set up the project locally.

### 1. Clone the Repository

```bash
git clone https://github.com/Marcos1236/DreamyURL.git
cd dreamyurl
```

### 2. Environment Configuration
Create a file named `.env` in `/app/src/.env` (or project root depending on your setup) with the following content:

**Note:** You must obtain a Google Safe Browsing API Key.

```
# Google Safe Browsing Configuration
SAFEBROWSING_API_KEY=****************
SAFEBROWSING_API_URL=https://safebrowsing.googleapis.com/v4/threatMatches:find
SAFEBROWSING_RABBIT_QUEUE=nqq-check

# Redis Configuration
REDIS_HOST=localhost
REDIS_PORT=6379

# Geolocation
GEO_API_KEY=
```

### 3. Infrastructure Setup (Docker)
This project requires **RabbitMQ** and **Redis**.

#### RabbitMQ
Ensure you are in the project root where the `rabbitmq/` folder exists.

```bash
docker compose up -d
```

If you prefer to run it separately, you can execute the following commands:

```bash
docker pull rabbitmq:4.1.0-management

docker run -d --name dreamyurl-rabbitmq \
  -p 5672:5672 -p 15672:15672 \
  -e RABBITMQ_DEFAULT_USER=guest -e RABBITMQ_DEFAULT_PASS=guest \
  -v "$(pwd)/rabbitmq/rabbitmq.conf":/etc/rabbitmq/rabbitmq.conf:ro \
  -v "$(pwd)/rabbitmq/definitions.json":/etc/rabbitmq/definitions.json:ro \
  rabbitmq:4.1.0-management
```
Without this queue, URL verification jobs will remain pending.

#### Redis
```bash
docker run -p 6379:6379 -d --name dreamyurl-redis redis:7
```

### 4. Run the Application

```bash
./gradlew bootRun
```
The application will be available at **http://localhost:8080**.

---

## 📋 Core Functionalities

### 1. Asynchronous Safe Browsing (Google Safe Browsing)
- Returns **202 Accepted** immediately.
- Enqueues validation in RabbitMQ.
- Resilience with **Rate Limiting** + **Retries**.

### 2. Intelligent URL Accessibility (Reachability)
- Smart check using **HEAD → GET fallback**.
- Redis caching prevents excessive network calls.

### 3. Advanced Geolocation
- Geolocation processed asynchronously.
- Dual‑provider failover (ipapi.co → ip-api.com).
- Cached in Redis.

### 4. High-Performance Analytics
- Real‑time counters for clicks, devices, locations.
- Redis **HINCRBY** for massive concurrency.

### 5. QR Code Generation
- Supports **PNG / JPEG / SVG**.
- Cached in Redis (binary).

---

## 🏗️ Architecture
Follows **Hexagonal Architecture**:

- **core**: Domain model, value objects, use cases, ports.
- **repositories**: JPA (HSQLDB) + Redis adapters.
- **delivery**: REST controllers + RabbitMQ workers.
- **app**: Configuration and wiring.

---

## Workflows

### 1. Asynchronous URL Creation Pipeline
![Create Short URL Flow](docs/DiagramaCreateShortUrl.png)

### 2. Intelligent URL Reachability Check
![Reachability Check Flow](docs/ReackabilityDiagram.png)

### 3. Resilient Safe Browsing Validation
![Safe Browsing Flow](docs/DiagramaGoogleSafeBrowser.png)

### 4. Async Geolocation with Failover
![Geolocation Flow](docs/Geolocation.png)

---

## 🌐 API Documentation
- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI Spec: http://localhost:8080/v3/api-docs

---

## 🧪 Testing

```bash
./gradlew test
./gradlew test jacocoTestReport
```

Integration tests use mocked RabbitMQ and HTTP interactions for fast feedback.

