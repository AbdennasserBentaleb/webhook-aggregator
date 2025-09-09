package com.resilient.webhook;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record WebhookEvent(
    UUID id,
    Instant receivedAt,
    String eventType,
    Map<String, Object> payload
) {
    public WebhookEvent(String eventType, Map<String, Object> payload) {
        this(UUID.randomUUID(), Instant.now(), eventType, payload);
    }
}
