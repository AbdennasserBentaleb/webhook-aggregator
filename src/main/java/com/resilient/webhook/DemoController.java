package com.resilient.webhook;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Demo endpoint so recruiters can trigger a burst of test webhooks
 * directly from the dashboard UI — no tools required.
 */
@RestController
@RequestMapping("/api/demo")
public class DemoController {

    private final WebhookProducer webhookProducer;

    public DemoController(WebhookProducer webhookProducer) {
        this.webhookProducer = webhookProducer;
    }

    @PostMapping("/fire")
    public ResponseEntity<Map<String, Object>> fireDemoBurst(
            @RequestParam(defaultValue = "20") int count) {

        int capped = Math.min(count, 500); // Safety cap

        String[] eventTypes = {
            "order.placed", "payment.confirmed", "user.signup",
            "shipment.dispatched", "invoice.generated", "subscription.renewed"
        };

        for (int i = 0; i < capped; i++) {
            String type = eventTypes[i % eventTypes.length];
            WebhookEvent event = new WebhookEvent(type, Map.of(
                    "source", "demo-burst",
                    "sequence", i + 1,
                    "batchSize", capped
            ));
            webhookProducer.enqueue(event);
        }

        return ResponseEntity.ok(Map.of(
                "enqueued", capped,
                "message", "Demo burst of " + capped + " webhooks enqueued successfully"
        ));
    }
}
