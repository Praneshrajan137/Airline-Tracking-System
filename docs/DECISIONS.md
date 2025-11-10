# Architecture Decision Records (ADRs)

**Document Version:** 1.0.0  
**Last Updated:** 2025-11-10  
**Status:** Approved

This document records all major architectural decisions for the Airline Tracking System, including the rationale, trade-offs, and alternatives considered.

---

## 1. Real-Time Data API Provider

**Choice:** FlightAware AeroAPI

**Justification:**

- **Comprehensive Coverage:** FlightAware provides real-time flight tracking data for commercial airlines worldwide with high accuracy and low latency
- **Well-Documented API:** RESTful API with clear documentation, JSON responses, and predictable error handling
- **Cost-Effective:** Free tier available (~$5/month for ~500 queries) suitable for MVP, with clear upgrade path
- **Reliability:** Industry-standard provider used by major travel applications, with 99.9% uptime SLA
- **Data Richness:** Provides comprehensive flight data including position, status, delays, gate information, and aircraft details
- **Rate Limits:** Transparent rate limiting that aligns with our caching strategy

**Alternatives Considered:**

- **FlightRadar24 API:** More expensive, less developer-friendly documentation
- **AviationStack API:** Limited real-time position data, higher cost
- **OpenSky Network:** Free but limited to European flights, less reliable

**Trade-offs:**

- ✅ **Gain:** Reliable, industry-standard data source with excellent documentation
- ✅ **Gain:** Reasonable pricing for MVP with clear scaling path
- ⚠️ **Accept:** Rate limits require aggressive caching (mitigated by Redis)
- ⚠️ **Accept:** Dependency on external service (mitigated by circuit breaker pattern)

**References:**

- [FlightAware AeroAPI Documentation](https://www.flightaware.com/commercial/aeroapi/)
- [AeroAPI Pricing](https://www.flightaware.com/commercial/aeroapi/pricing/)

---

## 2. Architecture Style

**Choice:** Microservices Architecture

**Justification:**

- **Separation of Concerns:** Each service has a single, well-defined responsibility (SRP)
- **Independent Scaling:** flightdata-service can scale independently from llm-summary-service based on load
- **Technology Flexibility:** Different services can use different libraries/frameworks if needed (though we standardize on Spring Boot)
- **Fault Isolation:** Failure in one service doesn't cascade to others
- **Team Autonomy:** Different teams can work on different services independently
- **Deployment Independence:** Services can be deployed separately without affecting others

**Alternatives Considered:**

- **Monolithic Architecture:** Simpler initially but harder to scale and maintain
- **Serverless (AWS Lambda):** Higher operational complexity, cold start issues, vendor lock-in
- **Service Mesh (Istio):** Overkill for MVP, adds complexity

**Trade-offs:**

- ✅ **Gain:** Scalability, maintainability, fault isolation
- ✅ **Gain:** Clear service boundaries and responsibilities
- ⚠️ **Accept:** Increased operational complexity (service discovery, distributed tracing)
- ⚠️ **Accept:** Network latency between services (mitigated by caching and async communication)
- ⚠️ **Accept:** More complex testing (mitigated by Testcontainers)

**References:**

- [Microservices Patterns by Chris Richardson](https://microservices.io/patterns/microservices.html)
- [Spring Cloud Microservices Documentation](https://spring.io/projects/spring-cloud)

---

## 3. API Gateway

**Choice:** Spring Cloud Gateway

**Justification:**

- **Spring Ecosystem Integration:** Native integration with Spring Boot, Eureka, and other Spring Cloud components
- **Reactive Performance:** Built on Spring WebFlux (reactive stack) for high throughput and low latency
- **Built-in Features:** Rate limiting, load balancing, routing, CORS, and circuit breaking out of the box
- **Programmatic Configuration:** Java-based configuration aligns with our Java stack
- **No Additional Infrastructure:** Runs as Spring Boot application, no separate gateway server needed
- **Filter Chain:** Flexible filter system for custom logic (logging, authentication, etc.)

**Alternatives Considered:**

- **Kong:** Requires separate infrastructure, more complex setup, overkill for MVP
- **AWS API Gateway:** Vendor lock-in, higher cost, less control
- **NGINX:** Lower-level configuration, requires separate service discovery integration
- **Zuul (Netflix):** Deprecated, replaced by Spring Cloud Gateway

**Trade-offs:**

- ✅ **Gain:** Native Spring integration, reactive performance, built-in features
- ✅ **Gain:** Single entry point for all client requests
- ⚠️ **Accept:** Learning curve for reactive programming (mitigated by Spring documentation)
- ⚠️ **Accept:** Single point of failure (mitigated by multiple gateway instances)

**References:**

- [Spring Cloud Gateway Documentation](https://spring.io/projects/spring-cloud-gateway)
- [Spring Cloud Gateway Performance](https://spring.io/blog/2018/08/27/spring-cloud-gateway-2-0-milestone-1)

---

## 4. Service Discovery

**Choice:** Netflix Eureka

**Justification:**

- **Spring Cloud Integration:** First-class support in Spring Cloud, mature and stable
- **Self-Preservation:** Handles network partitions gracefully with self-preservation mode
- **Simple REST API:** Easy to integrate and debug
- **Health Checks:** Built-in health check mechanism for service instances
- **Client-Side Load Balancing:** Ribbon integration for client-side load balancing
- **Mature & Battle-Tested:** Used in production by Netflix and thousands of companies

**Alternatives Considered:**

- **Consul:** More features (KV store, service mesh) but higher complexity, overkill for MVP
- **Zookeeper:** Lower-level, requires more operational expertise
- **Kubernetes Service Discovery:** Requires Kubernetes, vendor lock-in
- **AWS ECS Service Discovery:** AWS-specific, vendor lock-in

**Trade-offs:**

- ✅ **Gain:** Simple, mature, well-integrated with Spring Cloud
- ✅ **Gain:** Self-preservation mode handles network issues gracefully
- ⚠️ **Accept:** Eventually consistent (acceptable for our use case)
- ⚠️ **Accept:** Requires separate Eureka server instances (mitigated by Docker Compose)

**References:**

- [Netflix Eureka Documentation](https://github.com/Netflix/eureka)
- [Spring Cloud Netflix Eureka](https://spring.io/projects/spring-cloud-netflix)

---

## 5. Asynchronous Communication

**Choice:** Apache Kafka

**Justification:**

- **High Throughput:** Handles millions of messages per second, suitable for scaling
- **Durability:** Messages persisted to disk, survives broker failures
- **Partitioning:** Horizontal scaling through topic partitioning
- **Consumer Groups:** Multiple consumers can process messages in parallel
- **Replay Capability:** Can replay messages for reprocessing or debugging
- **Event Streaming:** Supports event sourcing and CQRS patterns
- **Industry Standard:** Widely adopted, excellent tooling and monitoring

**Alternatives Considered:**

- **RabbitMQ:** Simpler setup but lower throughput, less suitable for event streaming
- **Amazon SQS:** Vendor lock-in, less control, higher cost at scale
- **Redis Pub/Sub:** Not durable, messages lost if consumer offline
- **Apache Pulsar:** More features but higher complexity, less mature ecosystem

**Trade-offs:**

- ✅ **Gain:** High throughput, durability, replay capability, industry standard
- ✅ **Gain:** Decouples flightdata-service from llm-summary-service
- ⚠️ **Accept:** Higher operational complexity (Zookeeper/Kafka cluster)
- ⚠️ **Accept:** Steeper learning curve (mitigated by Spring Kafka abstraction)
- ⚠️ **Accept:** More resource intensive (mitigated by Docker Compose for local dev)

**References:**

- [Apache Kafka Documentation](https://kafka.apache.org/documentation/)
- [Spring for Apache Kafka](https://spring.io/projects/spring-kafka)
- [Kafka vs RabbitMQ Comparison](https://www.confluent.io/blog/kafka-fastest-messaging-system/)

---

## 6. Database for Persistence

**Choice:** PostgreSQL 15+

**Justification:**

- **ACID Compliance:** Full ACID transactions ensure data consistency for summaries
- **JSON Support:** Native JSON/JSONB support for flexible schema if needed
- **Rich Feature Set:** Advanced features (full-text search, arrays, custom types)
- **Performance:** Excellent query performance, supports complex queries
- **Open Source:** No licensing costs, active community
- **Mature & Stable:** Battle-tested in production environments
- **Spring Integration:** Excellent Spring Data JPA support
- **Docker Support:** Easy to run locally and in production

**Alternatives Considered:**

- **MySQL 8.0:** Similar features but less advanced JSON support, Oracle ownership concerns
- **MongoDB:** NoSQL, eventual consistency not suitable for critical summary data
- **SQLite:** Not suitable for production, no concurrent write support
- **Amazon RDS:** Vendor lock-in, higher cost

**Trade-offs:**

- ✅ **Gain:** ACID compliance, rich features, excellent Spring integration
- ✅ **Gain:** Open source, no licensing costs
- ⚠️ **Accept:** Requires connection pooling (mitigated by HikariCP in Spring Boot)
- ⚠️ **Accept:** Vertical scaling limitations (mitigated by read replicas if needed)

**References:**

- [PostgreSQL Documentation](https://www.postgresql.org/docs/)
- [Spring Data JPA](https://spring.io/projects/spring-data-jpa)
- [PostgreSQL vs MySQL Comparison](https://www.postgresql.org/about/featurematrix/)

---

## 7. Database for Caching

**Choice:** Redis 7+

**Justification:**

- **In-Memory Performance:** Sub-millisecond latency, perfect for caching use case
- **Data Structures:** Rich data structures (strings, hashes, sets) for flexible caching
- **TTL Support:** Built-in expiration (TTL) aligns with our 5-minute cache requirement
- **Persistence Options:** Can persist to disk if needed (RDB/AOF)
- **High Availability:** Redis Sentinel for failover, Redis Cluster for horizontal scaling
- **Spring Integration:** Excellent Spring Data Redis support
- **Industry Standard:** Widely adopted, excellent tooling

**Alternatives Considered:**

- **Memcached:** Simpler but less features, no persistence options
- **Hazelcast:** In-memory data grid, overkill for simple caching
- **Caffeine (In-Process):** Faster but not shared across instances, doesn't fit microservices
- **PostgreSQL (for caching):** Too slow, not designed for caching

**Trade-offs:**

- ✅ **Gain:** Sub-millisecond latency, built-in TTL, excellent Spring integration
- ✅ **Gain:** Reduces FlightAware API calls, improves response time
- ⚠️ **Accept:** Additional infrastructure component (mitigated by Docker Compose)
- ⚠️ **Accept:** Memory cost (mitigated by TTL and eviction policies)

**References:**

- [Redis Documentation](https://redis.io/docs/)
- [Spring Data Redis](https://spring.io/projects/spring-data-redis)
- [Redis Performance Benchmarks](https://redis.io/docs/management/optimization/benchmarks/)

---

## 8. LLM Provider

**Choice:** OpenAI GPT-3.5-turbo

**Justification:**

- **Cost-Effective:** GPT-3.5-turbo is significantly cheaper than GPT-4 ($0.0015/1K tokens vs $0.03/1K tokens)
- **Performance:** Fast response times (~500ms-2s) suitable for async processing
- **Quality:** Sufficient quality for generating concise flight summaries (2-3 sentences)
- **API Maturity:** Well-documented REST API with clear error handling
- **Token Limits:** 150 tokens max is sufficient for our use case
- **Temperature Control:** Low temperature (0.3) ensures factual, deterministic summaries
- **Industry Standard:** Widely adopted, reliable infrastructure

**Alternatives Considered:**

- **GPT-4:** Higher quality but 20x more expensive, overkill for simple summaries
- **Claude (Anthropic):** Similar cost/quality but less mature API, smaller ecosystem
- **Google Gemini:** Less mature, fewer examples/documentation
- **Local LLMs (Llama, Mistral):** Requires GPU infrastructure, higher operational complexity

**Trade-offs:**

- ✅ **Gain:** Cost-effective, fast, sufficient quality for summaries
- ✅ **Gain:** Well-documented API, reliable infrastructure
- ⚠️ **Accept:** External dependency (mitigated by retry logic and fallback)
- ⚠️ **Accept:** Token costs scale with usage (mitigated by caching summaries in DB)
- ⚠️ **Accept:** Rate limits (mitigated by async processing and retry logic)

**References:**

- [OpenAI API Documentation](https://platform.openai.com/docs/)
- [OpenAI Pricing](https://openai.com/pricing)
- [GPT-3.5-turbo vs GPT-4 Comparison](https://platform.openai.com/docs/models)

---

## 9. Programming Language & Version

**Choice:** Java 17 (LTS)

**Justification:**

- **Long-Term Support:** LTS release supported until September 2029, ensures stability
- **Spring Boot 3.x Requirement:** Spring Boot 3.x requires Java 17+ (Java 8/11 not supported)
- **Modern Features:** Records, pattern matching, text blocks, sealed classes improve code quality
- **Performance:** Significant performance improvements over Java 11 (ZGC, improved GC)
- **Enterprise Standard:** Widely adopted in enterprise environments
- **Tooling:** Excellent IDE support (IntelliJ IDEA, Eclipse), mature ecosystem
- **Team Expertise:** Common language in enterprise Java development

**Alternatives Considered:**

- **Java 11:** Older LTS but Spring Boot 3.x requires Java 17+
- **Java 21:** Newer LTS but less mature ecosystem, potential compatibility issues
- **Kotlin:** JVM language but team expertise in Java, adds complexity
- **Go/Rust:** Different runtime, would require rewriting Spring Boot ecosystem

**Trade-offs:**

- ✅ **Gain:** LTS support, modern features, Spring Boot 3.x compatibility
- ✅ **Gain:** Enterprise standard, excellent tooling
- ⚠️ **Accept:** Higher memory usage than Go/Rust (acceptable for our use case)
- ⚠️ **Accept:** Longer startup time than native languages (mitigated by Spring Boot optimizations)

**References:**

- [Java 17 Release Notes](https://www.oracle.com/java/technologies/javase/17-relnote-issues.html)
- [Spring Boot 3.x Requirements](https://spring.io/projects/spring-boot)
- [Java 17 Features](https://openjdk.org/projects/jdk/17/)

---

## 10. Build Tool

**Choice:** Apache Maven 3.8+

**Justification:**

- **Spring Boot Default:** Spring Boot projects default to Maven, best integration
- **Dependency Management:** Centralized dependency management via pom.xml
- **Plugin Ecosystem:** Rich plugin ecosystem (surefire, failsafe, docker, etc.)
- **Mature & Stable:** Battle-tested, widely adopted in enterprise
- **IDE Support:** Excellent support in IntelliJ IDEA, Eclipse, VS Code
- **Reproducible Builds:** Deterministic builds with dependency locking
- **Team Familiarity:** Most Java developers familiar with Maven

**Alternatives Considered:**

- **Gradle:** More flexible but steeper learning curve, less standard for Spring Boot
- **Ant/Ivy:** Legacy, not recommended for new projects
- **Bazel:** Overkill for microservices, higher complexity

**Trade-offs:**

- ✅ **Gain:** Spring Boot default, mature, excellent IDE support
- ✅ **Gain:** Standard XML configuration, easy to understand
- ⚠️ **Accept:** XML verbosity (mitigated by Spring Boot parent POM)
- ⚠️ **Accept:** Slower than Gradle for large projects (acceptable for microservices)

**References:**

- [Apache Maven Documentation](https://maven.apache.org/guides/)
- [Spring Boot Maven Plugin](https://docs.spring.io/spring-boot/docs/current/maven-plugin/reference/htmlsingle/)
- [Maven vs Gradle Comparison](https://www.baeldung.com/maven-vs-gradle)

---

## 11. Testing Framework

**Choice:** JUnit 5 + Mockito + Testcontainers

**Justification:**

- **JUnit 5:** Modern testing framework, standard for Java projects, excellent Spring Boot integration
- **Mockito:** Industry-standard mocking framework, easy to use, well-documented
- **Testcontainers:** Integration testing with real databases (PostgreSQL, Redis, Kafka) in Docker
- **Spring Boot Test:** Excellent testing support with @SpringBootTest, @WebMvcTest, @DataJpaTest
- **AssertJ:** Fluent assertions improve test readability
- **Coverage Tools:** JaCoCo integration for code coverage reporting

**Alternatives Considered:**

- **JUnit 4:** Legacy, not recommended for new projects
- **TestNG:** Less popular, less Spring Boot integration
- **Mockito Alternatives (EasyMock, JMock):** Less popular, smaller community
- **In-Memory Databases (H2):** Different behavior than PostgreSQL, not suitable for integration tests

**Trade-offs:**

- ✅ **Gain:** Modern, well-integrated, industry standard
- ✅ **Gain:** Testcontainers provides real integration testing
- ⚠️ **Accept:** Testcontainers slower than mocks (acceptable for integration tests)
- ⚠️ **Accept:** Requires Docker for Testcontainers (mitigated by Docker Desktop)

**References:**

- [JUnit 5 Documentation](https://junit.org/junit5/docs/current/user-guide/)
- [Mockito Documentation](https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html)
- [Testcontainers Documentation](https://www.testcontainers.org/)
- [Spring Boot Testing](https://spring.io/guides/gs/testing-web/)

---

## 12. Deployment Platform

**Choice:** Docker + Docker Compose (Local) / Kubernetes (Production)

**Justification:**

- **Docker Compose (Local Development):**
  - **Simplicity:** Single command to start all services (`docker-compose up`)
  - **Consistency:** Same environment across developers
  - **Isolation:** Each service runs in isolated container
  - **Easy Cleanup:** `docker-compose down` removes everything

- **Kubernetes (Production):**
  - **Industry Standard:** Widely adopted for container orchestration
  - **Auto-Scaling:** Horizontal Pod Autoscaling based on CPU/memory
  - **Self-Healing:** Automatic restart of failed containers
  - **Load Balancing:** Built-in load balancing across service instances
  - **Rolling Updates:** Zero-downtime deployments
  - **Portability:** Works on any cloud provider (AWS, GCP, Azure)

**Alternatives Considered:**

- **Heroku:** Simpler but vendor lock-in, higher cost, less control
- **AWS ECS:** AWS-specific, vendor lock-in
- **Docker Swarm:** Simpler than Kubernetes but less features, smaller ecosystem
- **VM Deployment:** Higher operational overhead, slower scaling

**Trade-offs:**

- ✅ **Gain:** Consistency between dev and prod, industry standard
- ✅ **Gain:** Containerization provides isolation and portability
- ⚠️ **Accept:** Docker/Kubernetes learning curve (mitigated by documentation)
- ⚠️ **Accept:** Additional infrastructure complexity (mitigated by Docker Compose for local)

**References:**

- [Docker Documentation](https://docs.docker.com/)
- [Docker Compose Documentation](https://docs.docker.com/compose/)
- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [Spring Boot Docker Guide](https://spring.io/guides/gs/spring-boot-docker/)

---

## Summary

All technology decisions align with the PRD requirements and support:

- **Performance:** Sub-500ms cached responses, sub-2s summary generation
- **Scalability:** Horizontal scaling of microservices
- **Reliability:** 99.9% uptime through redundancy and fault tolerance
- **Maintainability:** Clean architecture, SOLID principles, >90% test coverage
- **Cost-Effectiveness:** Open source stack, efficient resource usage

**Next Steps:** See ARCHITECTURE.md for detailed system design.

