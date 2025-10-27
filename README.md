# Webhook Aggregator

This is a Spring Boot gateway designed to buffer and rate-limit incoming webhook traffic, preventing downstream microservices from being overwhelmed by burst traffic. It handles payload ingestion, signature validation, queue buffering, and rate-limited consumption.

External webhook bursts can overwhelm internal services, exhausting thread pools and dropping payloads. This service sits at the network edge to solve this.

## System Architecture
```text
External Webhook --> [Ingestion & Validation] --> [RabbitMQ Queue] --> [Token Bucket Wait] --> [Internal Processing]
                                                                                                        |
                                                                                                  Resilience4j & DLQ
```

The system ingests at the edge, validates the HMAC-SHA256 signature to reject spoofed payloads immediately, buffers valid events in RabbitMQ, and enforces an egress rate via a custom Token Bucket limiter to protect downstream APIs.

## Architecture Decisions & Trade-offs

1. **Custom `wait/notify` Rate Limiter vs. Polling:**  
   Standard polling-based limiters or distributed Redis limiters introduce network hops and significant CPU jitter. By using a custom synchronization mechanism `wait(timeout) / notifyAll()` across threads, we achieve nanosecond-level precision.
   *Challenge Faced:* Ensuring strict thread safety under brutal concurrency. Native JVM `wait/notify` logic is notoriously error-prone, but exhaustive `CountDownLatch` multi-threaded unit tests were implemented to guarantee no race conditions or thread starvation occur under max load.
   
2. **RabbitMQ vs. Kafka:**  
   RabbitMQ was deliberately chosen over Kafka. For webhook payloads, we require granular per-message acknowledgment, explicit routing/bindings, and native Dead Letter Queue (DLQ) capabilities for independent retries, which is difficult to replicate with Kafka's offset-based partitions.  
   *Limitation:* Without Kafka, we sacrifice the ability to easily replay a historical log of events from days prior, meaning if a payload is permanently dead-lettered and drops past the TTL, it is unrecoverable.

3. **Java GC Pauses vs. Low-Latency Objectives:**  
   While relying on sleep/wait primitives inside the JVM, we remain vulnerable to Garbage Collection "Stop The World" pauses. In extreme burst scenarios where heap fragmentation triggers a major GC cycle, the rate-limiter will momentarily halt, skewing the emission rate unpredictably for that fraction of a second. A strict ultra-low latency system would ideally be written in Rust/C++, but the productivity of the JVM ecosystem with Spring Boot outweighs this tail-latency risk for our current workload.

## Local Execution & Reviewer Experience

### Full Integration Mode
To spin up the entire ecosystem (RabbitMQ + the Application) using docker-compose:
```bash
docker-compose up -d --build
```
*Application available at `localhost:8080`, RabbitMQ Admin console at `localhost:15672`.*

### Mock / Graceful Degradation Mode (Fast Startup)
If you don't want to install or run Docker for RabbitMQ, you can execute the app locally using Spring Boot without the broker. This degradation mode runs entirely in-memory using Spring AMQP mock boundaries or an embedded profile (if enabled). Be aware that standard startup without the broker will simply retry the connection gracefully in the background while still serving ingestion limits.
```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.rabbitmq.host=localhost"
```

### Testing Strategy
The test suite heavily employs **Testcontainers** to spin up throwing-away instances of RabbitMQ to validate the consumer retry topology and Dead Letter exchanges. Concurrency stability is proven via exhaustive multi-threaded unit tests acting upon the `TokenBucketRateLimiter`.

```bash
./mvnw clean test
```

## Configuration (12-Factor App)

Configuration is managed via environment variables to demonstrate 12-factor cloud deployment compliance:

| Variable | Default (Local) | 
| :--- | :--- | 
| `RABBITMQ_HOST` | `rabbitmq` |
| `RABBITMQ_PORT` | `5672` |
| `WEBHOOK_HMAC_SECRET` | *(Must be injected securely)* | 

For Kubernetes deployment, the manifests in `k8s/` leverage read-only filesystems, non-root user execution (UID 65532), and dropped capabilities for cloud-native security hardening. Liveness/Readiness probes are mapped to Spring Actuator.
