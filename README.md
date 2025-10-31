# Webhook Aggregator

This is a Spring Boot gateway designed to buffer and rate-limit incoming webhook traffic, preventing downstream microservices from being overwhelmed by burst traffic. It handles payload ingestion, signature validation, queue buffering, and rate-limited consumption.

External webhook bursts can overwhelm internal services, exhausting thread pools and dropping payloads. This service sits at the network edge to solve this.

## Tech Stack
- **Java 25** (with Preview Features enabled)
- **Spring Boot 3.5.x** / **Spring Cloud 2024.x**
- **RabbitMQ** (Message Broker for durable buffering)
- **Docker & Docker Compose** (Containerization & Orchestration)
- **Resilience4j** (Circuit Breaker for downstream protection)
- **Testcontainers** (Robust E2E/Integration Testing)
- **OpenAPI / Swagger** (API Documentation)
- **Kubernetes (K8s)** (Deployment manifests included)

## High-Level Architecture
```text
[ External Webhook ]
         |
         | HTTP POST /v1/webhooks
         |
[ Ingestion & Validation ]
  - HMAC-SHA256 Verification
  - Returns 202 Accepted instantly
         |
         | Enqueue Messages
         |
[ RabbitMQ ] (webhook-exchange)
  - Durable queue: incoming-webhooks
  - Dead Letter Exchange (DLX) for poison payloads
         |
         | Consume Messages (Paced)
         |
[ Token Bucket Wait & Internal Processing ]
  - Precise Rate Limiter (Nanosecond accuracy)
  - Resilience4j Circuit Breaker
         |
    +----+----+
    |         |
 Success    Failure (To DLQ)
```

The system ingests at the edge, validates the HMAC-SHA256 signature to reject spoofed payloads immediately, buffers valid events in RabbitMQ, and enforces an egress rate via a custom Token Bucket limiter to protect downstream APIs.

## Architecture Decisions & Trade-offs

1. **Custom `wait/notify` Rate Limiter vs. Polling:**  
   Standard polling-based limiters or distributed Redis limiters introduce network hops and CPU jitter. By using a custom synchronization mechanism `wait(timeout) / notifyAll()`, we achieve nanosecond-level precision.
   
2. **RabbitMQ vs. Kafka:**  
   RabbitMQ was deliberately chosen over Kafka. For webhook payloads, we require granular per-message acknowledgment, explicit routing/bindings, and native Dead Letter Queue (DLQ) capabilities for independent retries, which is difficult to replicate with Kafka's offset-based partitions.  

## Getting Started

Follow these step-by-step instructions to run the project locally.

### Prerequisites
- Docker and Docker Compose installed.
- (Optional) JDK 25 and Maven for local development.

### 1. Build and Run via Docker Compose (Recommended)
To spin up the entire ecosystem (RabbitMQ + Webhook Aggregator) in isolated containers:
```bash
docker-compose up -d --build
```
*Note: This command will pull the base images, build the Java application via a multi-stage Docker build, and start both the Spring Boot app and RabbitMQ.*

### 2. Verify Services
Check the logs of the webhook aggregator to ensure successful startup:
```bash
docker-compose logs -f webhook-aggregator
```

Ensure the Spring Boot application is healthy:
```bash
curl http://localhost:8080/actuator/health
```

### 3. Accessing the Application

| Resource | URL |
| :--- | :--- |
| **Live Dashboard** | `http://localhost:8080` |
| **Swagger UI** | `http://localhost:8080/swagger-ui.html` |
| **RabbitMQ Admin Console** | `http://localhost:15673` (user: `webhook_admin`, pass: `secure_pass_123`) |
| **Health Endpoint** | `http://localhost:8080/actuator/health` |

## Live Recruiter Dashboard

A premium dark-mode operational dashboard is served at `http://localhost:8080`. It provides real-time visibility into the system for anyone evaluating the project:

- **Live Metrics** — Total webhooks processed, Token Bucket availability, Circuit Breaker state, Active consumers
- **Architecture Diagram** — Visual end-to-end flow with tech-stack pills
- **Live Event Feed** — Last 20 processed webhooks, colour-coded by event type, auto-refreshing every 2 seconds
- **Demo Burst Controls** — Preset quick-fire buttons (20, 50, 100, 300 webhooks) plus a custom number input (1–500) so recruiters can trigger any load size and watch the system respond in real-time

## API Documentation (Swagger/OpenAPI)
API documentation is automatically generated and served by `springdoc-openapi`.

Once the application is running, you can access the interactive Swagger UI here:
**[http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)**

The OpenAPI v3 specification JSON is available at:
`http://localhost:8080/v3/api-docs`

### Endpoints

| Method | Path | Description |
| :--- | :--- | :--- |
| `POST` | `/v1/webhooks` | Ingest a webhook (HMAC-validated, 202 Accepted) |
| `GET` | `/v1/status` | Processed count + recent webhook list |
| `POST` | `/v1/status/sign` | Generate an HMAC signature for a test payload |
| `GET` | `/api/dashboard` | Live JSON stats for the dashboard (processed count, token bucket, circuit breaker state) |
| `POST` | `/api/demo/fire?count=N` | Enqueue N demo webhooks (max 500) for recruiter demonstrations |
| `GET` | `/actuator/health` | Spring Boot health with RabbitMQ and circuit breaker status |

## Configuration (12-Factor App)

Configuration is managed via environment variables to demonstrate 12-factor cloud deployment compliance:

| Variable | Default (Local) | 
| :--- | :--- | 
| `RABBITMQ_HOST` | `rabbitmq` |
| `RABBITMQ_PORT` | `5672` |
| `RABBITMQ_USERNAME`| `webhook_admin` |
| `RABBITMQ_PASSWORD`| `secure_pass_123` |
| `WEBHOOK_HMAC_SECRET` | *(Must be injected securely)* | 

## Testing
The test suite heavily employs **Testcontainers** to spin up volatile instances of RabbitMQ to validate the consumer retry topology and Dead Letter exchanges. Concurrency stability is proven via exhaustive multi-threaded unit tests acting upon the `TokenBucketRateLimiter`.

```bash
./mvnw clean test
```
