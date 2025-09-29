package com.resilient.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WebhookController.class)
class WebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WebhookProducer webhookProducer;

    @MockBean
    private WebhookSecurityService securityService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldAcceptValidWebhook() throws Exception {
        Map<String, Object> payload = Map.of("event", "test.event");
        String rawPayload = objectMapper.writeValueAsString(payload);
        String signature = "test-signature";

        when(securityService.verifySignature(rawPayload, signature)).thenReturn(true);

        mockMvc.perform(post("/v1/webhooks")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Webhook-Signature", signature)
                .content(rawPayload))
                .andExpect(status().isAccepted());
    }

    @Test
    void shouldRejectInvalidSignature() throws Exception {
        Map<String, Object> payload = Map.of("event", "test.event");
        String rawPayload = objectMapper.writeValueAsString(payload);

        when(securityService.verifySignature(any(), any())).thenReturn(false);

        mockMvc.perform(post("/v1/webhooks")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Webhook-Signature", "wrong-signature")
                .content(rawPayload))
                .andExpect(status().isUnauthorized());
    }
}
