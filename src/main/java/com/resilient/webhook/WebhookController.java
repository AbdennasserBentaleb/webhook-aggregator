package com.resilient.webhook;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1/webhooks")
@Tag(name = "Webhook Ingestion", description = "Endpoints for receiving and queuing external webhooks")
public class WebhookController {

    private final WebhookProducer webhookProducer;
    private final WebhookSecurityService securityService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public WebhookController(WebhookProducer webhookProducer, 
                            WebhookSecurityService securityService,
                            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.webhookProducer = webhookProducer;
        this.securityService = securityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    @Operation(
        summary = "Receive a webhook",
        description = "Accepts a webhook payload, verifies the HMAC signature, and enqueues it for processing.",
        responses = {
            @ApiResponse(responseCode = "202", description = "Webhook accepted and enqueued"),
            @ApiResponse(responseCode = "401", description = "Invalid or missing signature"),
            @ApiResponse(responseCode = "400", description = "Invalid payload")
        }
    )
    public ResponseEntity<Void> receiveWebhook(
            @Valid @RequestBody Map<String, Object> payload,
            @Parameter(description = "HMAC signature of the payload (Base64 encoded)")
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature) {

        try {
            String rawPayload = objectMapper.writeValueAsString(payload);
            if (!securityService.verifySignature(rawPayload, signature)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return ResponseEntity.badRequest().build();
        }

        String eventType = (String) payload.getOrDefault("event", "unknown");
        WebhookEvent event = new WebhookEvent(eventType, payload);
        webhookProducer.enqueue(event);
        return ResponseEntity.accepted().build();
    }
}
