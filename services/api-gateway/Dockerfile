# syntax=docker/dockerfile:1.4

# Stage 1: Builder
FROM eclipse-temurin:17-jdk-alpine AS builder

# Set working directory
WORKDIR /app

# Install Maven
RUN apk add --no-cache maven

# Copy only pom.xml first (better layer caching)
COPY pom.xml .

# Download dependencies (cached layer if pom.xml doesn't change)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN mvn clean package -DskipTests -B && \
    mv target/*.jar app.jar

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine

# Metadata labels
LABEL maintainer="Airline Tracker Team"
LABEL service="api-gateway"
LABEL description="API Gateway for Airline Tracker System"
LABEL version="1.0.0"

# Build arguments
ARG VERSION=latest
ARG BUILD_DATE=unknown

# Environment labels
LABEL org.opencontainers.image.created="${BUILD_DATE}"
LABEL org.opencontainers.image.version="${VERSION}"
LABEL org.opencontainers.image.title="API Gateway"
LABEL org.opencontainers.image.description="Spring Cloud Gateway - Single entry point for all microservices"

# Create non-root user
RUN addgroup -g 1001 -S appuser && \
    adduser -u 1001 -S appuser -G appuser

# Set working directory
WORKDIR /app

# Copy JAR from builder stage
COPY --from=builder --chown=appuser:appuser /app/app.jar app.jar

# Switch to non-root user
USER appuser

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# JVM optimization flags
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
