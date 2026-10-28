package com.example.auditlog.api;

import java.io.IOException;

import com.example.auditlog.audit.InvalidEventException;
import com.example.auditlog.lifecycle.LifecycleReceipt;
import com.example.auditlog.lifecycle.LifecycleRequest;
import com.example.auditlog.lifecycle.LifecycleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LifecycleController {

    private final LifecycleRequest parser;
    private final LifecycleService service;

    public LifecycleController(LifecycleRequest parser, LifecycleService service) {
        this.parser = parser;
        this.service = service;
    }

    @PostMapping(value = "/audit/retention/runs", consumes = "application/json")
    @Operation(summary = "Archive one retention batch", description = "Requires AUDIT_LIFECYCLE. "
            + "Server ingestion age and configured policy determine eligibility. Same operation ID/request safely replays.",
            requestBody = @RequestBody(required = true, content = @Content(schema = @Schema(implementation = RetentionBody.class))))
    public LifecycleReceipt retain(HttpServletRequest request, @Parameter(hidden = true) Authentication authentication) throws IOException {
        return service.retain(parser.retention(body(request)), authentication.getName());
    }

    @PostMapping(value = "/audit/events/{eventId}/redactions", consumes = "application/json")
    @Operation(summary = "Erase selected field keys", description = "Requires AUDIT_LIFECYCLE, reason, approvalReference and "
            + "confirmation ERASE_FIELD_KEYS. Only fields protected at ingestion. Erasure affects active-store keys; backups remain a limitation.",
            requestBody = @RequestBody(required = true, content = @Content(schema = @Schema(implementation = RedactionBody.class))))
    public LifecycleReceipt redact(@PathVariable String eventId, HttpServletRequest request,
            @Parameter(hidden = true) Authentication authentication) throws IOException {
        return service.redact(LifecycleRequest.uuid(eventId), parser.redaction(body(request)), authentication.getName());
    }

    @Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
    public record RetentionBody(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) java.util.UUID operationId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 255) String reason) { }
    @Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
    public record RedactionBody(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) java.util.UUID operationId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 255) String reason,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 255) String approvalReference,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = "ERASE_FIELD_KEYS") String confirmation,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "1-20 unique RFC 6901 payload pointers") java.util.List<String> paths) { }

    private byte[] body(HttpServletRequest request) throws IOException {
        if (request.getContentLengthLong() > 16 * 1024) {
            throw new InvalidEventException("Lifecycle request exceeds 16 KiB", 413);
        }
        return request.getInputStream().readNBytes(16 * 1024 + 1);
    }
}
