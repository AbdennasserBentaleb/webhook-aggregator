package com.resilient.webhook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RabbitWebhookProducerTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private RabbitWebhookProducer rabbitWebhookProducer;

    @BeforeEach
    void setUp() {
        rabbitWebhookProducer = new RabbitWebhookProducer(rabbitTemplate, "webhook-exchange", "webhook.incoming");
    }

    @Test
    void shouldEnqueueEventToRabbitMQ() {
        WebhookEvent event = new WebhookEvent("test.event", Map.of("key", "value"));

        rabbitWebhookProducer.enqueue(event);

        verify(rabbitTemplate).convertAndSend(
            eq("webhook-exchange"),
            eq("webhook.incoming"),
            eq(event)
        );
    }
}
