package com.resilient.webhook;

import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Repository
public class ProcessedWebhookRepository {
    private final List<WebhookEvent> processed = Collections.synchronizedList(new LinkedList<>());
    private final AtomicInteger totalProcessed = new AtomicInteger(0);
    private static final int MAX_RECENT = 100;

    public void add(WebhookEvent event) {
        totalProcessed.incrementAndGet();
        processed.add(0, event);
        if (processed.size() > MAX_RECENT) {
            processed.remove(processed.size() - 1);
        }
    }

    public List<WebhookEvent> getRecent() {
        return List.copyOf(processed);
    }

    public int getCount() {
        return totalProcessed.get();
    }
}
