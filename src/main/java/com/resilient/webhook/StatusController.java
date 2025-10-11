package com.resilient.webhook;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/status")
public class StatusController {

    private final ProcessedWebhookRepository repository;
    private final RateLimiter rateLimiter;
    private final WebhookSecurityService securityService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public StatusController(ProcessedWebhookRepository repository, 
                          RateLimiter rateLimiter,
                          WebhookSecurityService securityService,
                          com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.repository = repository;
        this.rateLimiter = rateLimiter;
        this.securityService = securityService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public Map<String, Object> getStatus() {
        return Map.of(
            "processedCount", repository.getCount(),
            "recentWebhooks", repository.getRecent()
        );
    }

    @PostMapping("/sign")
    public Map<String, String> signPayload(@RequestBody Map<String, Object> payload) {
        try {
            String rawPayload = objectMapper.writeValueAsString(payload);
            return Map.of("signature", securityService.generateSignature(rawPayload));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize payload", e);
        }
    }
}
