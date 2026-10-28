package com.example.auditlog.audit;

import java.time.Instant;
import java.util.Map;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "WriteEventRequest", additionalProperties = Schema.AdditionalPropertiesValue.FALSE,
        description = "Strict JSON: unknown/duplicate fields rejected; maximum body 256 KiB.")
public record EventInput(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 255, example = "USER_LOGIN") String eventType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 255, example = "synthetic-actor") String actorId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 255, example = "CLIENT_ACCOUNT") String resourceType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 255, example = "synthetic-resource") String resourceId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Object, maximum depth 20. Configured sensitive paths are encrypted.")
        Map<String, Object> payload,
        @Schema(description = "Optional occurrence time; UTC-normalized, microsecond precision. Defaults to server ingestion time.") Instant timestamp) {
}
