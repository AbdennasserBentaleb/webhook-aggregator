package com.resilient.webhook;

public interface WebhookProducer {
    void enqueue(WebhookEvent event);
}
