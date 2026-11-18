FROM maven:3.9.16-eclipse-temurin-25-alpine AS build
WORKDIR /workspace
COPY pom.xml pom.xml
COPY services/service-registry/pom.xml services/service-registry/pom.xml
COPY services/api-gateway/pom.xml services/api-gateway/pom.xml
COPY services/flightdata-service/pom.xml services/flightdata-service/pom.xml
COPY services/llm-summary-service/pom.xml services/llm-summary-service/pom.xml
COPY integration-tests/pom.xml integration-tests/pom.xml
COPY performance-tests/pom.xml performance-tests/pom.xml
COPY services/api-gateway/src services/api-gateway/src
RUN mvn -B -ntp -f pom.xml -pl services/api-gateway -am -DskipTests package
RUN cp services/api-gateway/target/api-gateway-1.0.0.jar /workspace/app.jar

FROM eclipse-temurin:25-jre-alpine
ARG SERVICE_PORT=8080
ENV SERVICE_PORT=${SERVICE_PORT}
WORKDIR /app
RUN addgroup -S appgroup && adduser -S -G appgroup appuser
COPY --from=build --chown=appuser:appgroup /workspace/app.jar app.jar
USER appuser:appgroup
EXPOSE ${SERVICE_PORT}
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD-SHELL wget --no-verbose --tries=1 --spider "http://localhost:${SERVICE_PORT}/actuator/health" || exit 1
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]