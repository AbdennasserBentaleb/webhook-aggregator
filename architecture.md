# Architecture

## Core Philosophy

This service is a Spring Boot gateway positioned between external webhook providers and internal microservices.

Design constraints:
1. **Zero Data Loss:** Once a `202 Accepted` is returned, the payload must be processed, even during internal system downtime.
2. **Predictable Throughput:** The pace of traffic forwarded to internal systems must be strictly controlled.
3. **Operational Transparency:** Failures must be auditable with isolated storage for failed payloads.

## System Topology

```text
[ External Provider ]
         |
         | HTTP POST /v1/webhooks
         |
[ Ingestion Layer ]
  HMAC-SHA256 Verification
  202 Accepted immediately
         |
         | publish
         |
[ RabbitMQ: webhook-exchange ]
  Durable queue: incoming-webhooks
  DLX: webhook-exchange.dlx
         |
         | consume
         |
[ Consumer Layer ]
  Token Bucket: acquire token
  Circuit Breaker: call downstream
         |
    +----+----+
    |         |
 Success    Failure (after retries)
    |         |
 Processed  [ DLQ: incoming-webhooks.dlq ]
```

## Engineering Decisions

### 1. The Ingestion Layer

The controller at `POST /v1/webhooks` verifies the HMAC-SHA256 signature and enqueues the payload to RabbitMQ.

It returns an HTTP `202 Accepted` immediately without blocking for processing. Decoupling ingestion maintains latency under 50ms, allowing absorption of concurrent spikes without saturating HTTP threads. Unauthenticated requests are dropped.

### 2. Message Broker: RabbitMQ vs Kafka

RabbitMQ was selected over Kafka for webhook buffering due to specific queuing requirements.

Webhook processing requires fine-grained, per-message control. Kafka's partition-offset model can cause a single failed message to block a partition without custom DLQ logic. 

With RabbitMQ:
- I get native Dead Letter Exchange (DLX) routing. When a message exhausts its retry budget, Rabbit routes it to the DLQ automatically. Zero application code required.
- I get per-message ACKs.
- With durable queues and persistent messages, I get the exact same crash-tolerance as Kafka for this specific workload size.

### 3. Precision Rate Limiting

The system requirements mandated precise control over consumer consumption rates. A custom Token Bucket rate limiter was implemented to eliminate polling overhead.

**Resolution Precision:**
`System.nanoTime()` is used for token calculation. Millisecond resolution introduces rounding errors during high multi-threaded concurrency requests, whereas nanosecond resolution maintains strict mathematical bounds.

Instead of busy-looping, blocked threads use `wait(millis, nanos)` with exact calculated delays based on the token deficit, yielding flat CPU utilization even at the rate limit ceiling. 

### 4. Downstream Protection

The `WebhookConsumer` thread acquires a rate limit token before processing. This downstream call is wrapped in a Resilience4j `@CircuitBreaker`.

If the internal system returns an elevated error rate, the circuit trips. The consumer halts and rethrows, triggering Spring AMQP's exponential backoff. Upon exhausting max retries, RabbitMQ routes the message to the DLQ for manual inspection. 

## Observability

- **Logs:** Logback is configured for structured JSON (Logstash format) to support ELK/Loki ingestion.
- **Metrics:** Micrometer exposes custom Prometheus metrics:
  1. `webhook.ratelimit.tokens` (gauge): Current bucket capacity.
  2. `webhook.ratelimit.wait` (timer): Thread wait duration for token acquisition.
- **Health:** Spring Boot Actuator exposes `/actuator/health` for Kubernetes `liveness` and `readiness` probes.
