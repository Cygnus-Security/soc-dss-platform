package com.socdss.streaming;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.socdss.correlation.CorrelationService;
import com.socdss.ingestion.SecurityEventBatchResult;
import com.socdss.ingestion.SecurityEventIngestionService;
import com.socdss.ingestion.SecurityEventV1;
import com.socdss.response.ActionResultV1;
import com.socdss.response.ResponseActionService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class SocKafkaConsumers {
    private static final Set<String> RESULT_STATUSES = Set.of("EXECUTED", "FAILED", "DRY_RUN");

    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final SecurityEventIngestionService ingestionService;
    private final CorrelationService correlationService;
    private final ResponseActionService actionService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String deadLetterTopic;

    public SocKafkaConsumers(
            ObjectMapper objectMapper,
            Validator validator,
            SecurityEventIngestionService ingestionService,
            CorrelationService correlationService,
            ResponseActionService actionService,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${app.kafka.dead-letter-topic}") String deadLetterTopic
    ) {
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.ingestionService = ingestionService;
        this.correlationService = correlationService;
        this.actionService = actionService;
        this.kafkaTemplate = kafkaTemplate;
        this.deadLetterTopic = deadLetterTopic;
    }

    @KafkaListener(topics = "${app.kafka.normalized-topic}")
    public void normalizedEvent(String payload) {
        try {
            SecurityEventV1 event = objectMapper.readValue(payload, SecurityEventV1.class);
            Set<ConstraintViolation<SecurityEventV1>> violations = validator.validate(event);
            if (!violations.isEmpty()) {
                throw new IllegalArgumentException("SecurityEvent validation failed: " + violations.iterator().next().getMessage());
            }
            SecurityEventBatchResult result = ingestionService.ingest(java.util.List.of(event));
            if (result.imported() > 0) {
                correlationService.startCorrelationJob();
            }
        } catch (Exception e) {
            deadLetter("normalized_event", payload, e);
        }
    }

    @KafkaListener(topics = "${app.kafka.action-result-topic}")
    public void actionResult(String payload) {
        try {
            ActionResultV1 result = objectMapper.readValue(payload, ActionResultV1.class);
            if (!"1.0".equals(result.schemaVersion()) || result.actionId() == null
                    || !RESULT_STATUSES.contains(result.status()) || result.completedAt() == null) {
                throw new IllegalArgumentException("Invalid ActionResult v1");
            }
            actionService.applyResult(result, payload);
        } catch (Exception e) {
            deadLetter("action_result", payload, e);
        }
    }

    private void deadLetter(String source, String payload, Exception error) {
        try {
            String message = objectMapper.writeValueAsString(Map.of(
                    "schema_version", "1.0",
                    "source", source,
                    "failed_at", Instant.now().toString(),
                    "error", error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(),
                    "payload", payload
            ));
            kafkaTemplate.send(deadLetterTopic, source, message);
        } catch (Exception ignored) {
            // Listener must not crash-loop when both payload handling and DLQ publishing fail.
        }
    }
}
