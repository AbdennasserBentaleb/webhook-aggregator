# Product Requirements: Webhook Gateway

## Problem Statement

Integration with high-volume platforms (e.g., Stripe, Shopify, GitHub) frequently results in variable webhook delivery rates. Outages, batch updates, or flash events can trigger thousands of payloads concurrently.

Internal microservices typically lack the scaling required to instantly absorb massive traffic spikes. Consequently:
1. API thread pools saturate, delaying legitimate requests.
2. Database connection pools exhaust, causing HTTP 500 errors.
3. Dropped network requests during service deployments lead to permanent state loss due to limited upstream retry policies.

A mechanism is required to shield core services without dropping data.

## Solution Architecture

A dedicated Edge Gateway serves as a buffer between external networks and internal services. 

Workflow:
1. **Verify & Buffer:** The gateway verifies the HMAC signature. Valid payloads are immediately persisted to a durable RabbitMQ queue.
2. **ACK:** Returns an `HTTP 202 Accepted` within 50ms.
3. **Paced Consumption:** A background processor reads from the queue at a strictly controlled rate and forwards payloads to internal services.

System Guarantees:
- **Zero Data Loss:** `202` indicates persistence in RabbitMQ. If the downstream processor is offline, the queue acts as a buffer.
- **Traffic Shaping:** A Token Bucket rate limiter strictly bounds maximum throughput, preventing upstream spikes from reaching internal databases.
- **Poison Pill Isolation:** Consistently crashing payloads are routed to a Dead Letter Queue (DLQ) for manual review.

## Target Audience

- **Backend / Integration Engineers:** Dealing with third-party integration infrastructure.
- **SREs:** Engineering teams offloading traffic smoothing to the edge.

## Success Criteria

1. **Throughput:** The ingestion endpoint must accept payloads and return `202` in < 50ms, even if the queue has 100,000 pending messages.
2. **Resilience:** If the downstream internal service is killed mid-burst, exactly zero accepted webhooks are lost.
3. **Precision:** The rate limiter must allow expected bursts but strictly enforce the long-term throughput ceiling within a 5% margin of error, preventing downstream DBs from ever seeing a spike.
