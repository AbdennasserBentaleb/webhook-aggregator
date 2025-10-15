# Build
FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /app
COPY . .
RUN ./mvnw clean package -DskipTests

# Runtime
FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S appgroup && adduser -S appuser -G appgroup -u 65532 \
    && mkdir -p /app && chown -R appuser:appgroup /app
USER 65532
WORKDIR /app
COPY --from=builder --chown=appuser:appgroup /app/target/*.jar app.jar

ENV RABBITMQ_HOST=localhost
ENV RABBITMQ_PORT=5672

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
