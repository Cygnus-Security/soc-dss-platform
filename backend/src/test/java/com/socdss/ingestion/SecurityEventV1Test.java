package com.socdss.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityEventV1Test {
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void contractExampleDeserializesAndPassesBeanValidation() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/contracts/security-event-v1.json")) {
            assertThat(input).isNotNull();
            SecurityEventV1 event = objectMapper.readValue(input, SecurityEventV1.class);

            assertThat(event.schemaVersion()).isEqualTo("1.0");
            assertThat(event.sourceType()).isEqualTo(SecurityEventV1.SourceType.ids);
            assertThat(event.mitreTechniques()).containsExactly("T1190");
            assertThat(validator.validate(event)).isEmpty();
        }
    }
}
