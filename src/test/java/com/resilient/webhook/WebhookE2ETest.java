package com.resilient.webhook;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebhookE2ETest {

    @Container
    static RabbitMQContainer rabbitMQContainer = new RabbitMQContainer("rabbitmq:4.0-management-alpine");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", rabbitMQContainer::getHost);
        registry.add("spring.rabbitmq.port", rabbitMQContainer::getAmqpPort);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private WebhookSecurityService securityService;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Test
    void shouldEnforceRateLimitingDuringBurst() throws Exception {
        int numberOfRequests = 100; // 2x the capacity (50)

        // Capture baseline before sending the burst so the assertion is count-agnostic
        ResponseEntity<Map> baseline = restTemplate.getForEntity("/v1/status", Map.class);
        int initialCount = ((Number) baseline.getBody().get("processedCount")).intValue();
        
        long start = System.currentTimeMillis();
        for (int i = 0; i < numberOfRequests; i++) {
            Map<String, Object> payload = Map.of(
                "event", "burst.test",
                "id", "burst_" + i
            );
            
            String rawPayload = objectMapper.writeValueAsString(payload);
            String signature = securityService.generateSignature(rawPayload);

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("X-Webhook-Signature", signature);
            headers.set("Content-Type", "application/json");

            org.springframework.http.HttpEntity<Map<String, Object>> entity = new org.springframework.http.HttpEntity<>(payload, headers);
            restTemplate.postForEntity("/v1/webhooks", entity, Void.class);
        }
        
        // Wait until at least numberOfRequests new webhooks have been processed
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            ResponseEntity<Map> statusResponse = restTemplate.getForEntity("/v1/status", Map.class);
            int currentCount = ((Number) statusResponse.getBody().get("processedCount")).intValue();
            assertThat(currentCount).isGreaterThanOrEqualTo(initialCount + numberOfRequests);
        });
        
        long end = System.currentTimeMillis();
        long duration = end - start;
        
        // With 50 req/s and 100 requests:
        // First 50 are immediate (bucket capacity).
        // Next 50 take 1s to refill (50 tokens * 20ms/token = 1000ms).
        // Total time should be at least 1 second.
        assertThat(duration).isGreaterThanOrEqualTo(1000);
    }
}
