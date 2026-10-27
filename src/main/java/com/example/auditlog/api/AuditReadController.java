package com.example.auditlog.api;

import java.util.UUID;

import com.example.auditlog.audit.AuditQueryService;
import com.example.auditlog.audit.AuditQueryService.EventPage;
import com.example.auditlog.audit.AuditQueryService.EventView;
import com.example.auditlog.audit.ChainVerificationService;
import com.example.auditlog.audit.EventQuery;
import com.example.auditlog.audit.InvalidEventException;
import com.example.auditlog.audit.VerificationResult;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/audit")
public class AuditReadController {

    private final AuditQueryService queries;
    private final ChainVerificationService verification;

    public AuditReadController(AuditQueryService queries, ChainVerificationService verification) {
        this.queries = queries;
        this.verification = verification;
    }

    @GetMapping("/events")
    public EventPage events(@RequestParam MultiValueMap<String, String> parameters) {
        return queries.query(EventQuery.parse(parameters));
    }

    @GetMapping("/events/{eventId}")
    public EventView event(@PathVariable String eventId) {
        UUID id;
        try {
            id = UUID.fromString(eventId);
            if (!id.toString().equalsIgnoreCase(eventId)) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new InvalidEventException("eventId must be a UUID");
        }
        return queries.find(id);
    }

    @GetMapping("/verify")
    public VerificationResult verify(@RequestParam MultiValueMap<String, String> parameters) {
        if (!parameters.isEmpty()) {
            throw new InvalidEventException("Verification does not accept filters");
        }
        return verification.verify();
    }
}
