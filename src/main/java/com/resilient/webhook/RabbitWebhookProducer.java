package com.resilient.webhook;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RabbitWebhookProducer implements WebhookProducer {

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String routingKey;

    public RabbitWebhookProducer(
            RabbitTemplate rabbitTemplate,
            @Value("${webhook.queue.exchange}") String exchange,
            @Value("${webhook.queue.routing-key}") String routingKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.routingKey = routingKey;
    }

    @Override
    public void enqueue(WebhookEvent event) {
        rabbitTemplate.convertAndSend(exchange, routingKey, event);
    }
}
