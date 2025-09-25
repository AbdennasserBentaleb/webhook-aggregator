package com.resilient.webhook;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
public class WebhookConsumer {

    private static final Logger log = LoggerFactory.getLogger(WebhookConsumer.class);
    private final RateLimiter rateLimiter;
    private final ProcessedWebhookRepository repository;

    public WebhookConsumer(RateLimiter rateLimiter, ProcessedWebhookRepository repository) {
        this.rateLimiter = rateLimiter;
        this.repository = repository;
    }

    @RabbitListener(queues = "${webhook.queue.name}")
    @CircuitBreaker(name = "webhookProcessor", fallbackMethod = "handleProcessorFailure")
    public void consume(WebhookEvent event) throws InterruptedException {
        rateLimiter.acquire();
        processWebhook(event);
    }

    private void processWebhook(WebhookEvent event) {
        log.info("Processing webhook: id={}, type={}, receivedAt={}",
                event.id(), event.eventType(), event.receivedAt());

        if (Math.random() < 0.05) {
            throw new RuntimeException("Simulated internal processing failure");
        }

        repository.add(event);
    }

    public void handleProcessorFailure(WebhookEvent event, Throwable t) {
        log.error("Circuit breaker triggered or processor failed for webhook {}: {}", event.id(), t.getMessage());
        throw new RuntimeException("Rethrowing for RabbitMQ retry logic", t);
    }
}
