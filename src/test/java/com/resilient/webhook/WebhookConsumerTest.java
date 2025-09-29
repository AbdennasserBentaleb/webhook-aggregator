package com.resilient.webhook;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookConsumerTest {

    @Mock
    private RateLimiter rateLimiter;

    @Mock
    private ProcessedWebhookRepository repository;

    @InjectMocks
    private WebhookConsumer webhookConsumer;

    @Test
    void shouldAcquireRateLimitBeforeProcessing() throws InterruptedException {
        WebhookEvent event = new WebhookEvent("test.event", Map.of("key", "value"));
        
        webhookConsumer.consume(event);

        verify(rateLimiter).acquire();
    }
}
