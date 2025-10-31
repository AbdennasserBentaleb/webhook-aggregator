package com.resilient.webhook;

import io.micrometer.core.instrument.MeterRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardApiController {

    private final ProcessedWebhookRepository repository;
    private final MeterRegistry meterRegistry;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public DashboardApiController(ProcessedWebhookRepository repository,
                                  MeterRegistry meterRegistry,
                                  CircuitBreakerRegistry circuitBreakerRegistry) {
        this.repository = repository;
        this.meterRegistry = meterRegistry;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    @GetMapping
    public Map<String, Object> getDashboard() {
        // Rate limit tokens gauge
        double tokens = meterRegistry.find("webhook.ratelimit.tokens")
                .gauge() != null
                ? meterRegistry.find("webhook.ratelimit.tokens").gauge().value()
                : -1.0;

        // Circuit breaker state
        String cbState;
        try {
            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("webhookProcessor");
            cbState = cb.getState().name(); // CLOSED, OPEN, HALF_OPEN
        } catch (Exception e) {
            cbState = "UNKNOWN";
        }

        // Recent webhooks — return only what the UI needs
        List<Map<String, Object>> recent = repository.getRecent().stream()
                .limit(20)
                .map(e -> Map.<String, Object>of(
                        "id", e.id().toString().substring(0, 8),
                        "eventType", e.eventType(),
                        "receivedAt", e.receivedAt().toString()
                ))
                .collect(Collectors.toList());

        return Map.of(
                "processedCount", repository.getCount(),
                "rateLimitTokens", Math.round(tokens * 10.0) / 10.0,
                "circuitBreakerState", cbState,
                "recentWebhooks", recent
        );
    }
}
