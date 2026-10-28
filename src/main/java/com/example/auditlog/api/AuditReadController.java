package com.example.auditlog.api;

import java.util.UUID;

import com.example.auditlog.audit.AuditQueryService;
import com.example.auditlog.audit.AuditQueryService.EventPage;
import com.example.auditlog.audit.AuditQueryService.EventView;
import com.example.auditlog.audit.ChainVerificationService;
import com.example.auditlog.audit.EventQuery;
import com.example.auditlog.audit.InvalidEventException;
import com.example.auditlog.audit.VerificationResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import org.springframework.util.MultiValueMap;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
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
    @Operation(summary = "Query events", description = "Requires AUDIT_READ. AND filters; protected values are restored. "
            + "Times use [from,to). Repeat filters and limit with nextCursor; later appends are excluded.")
    @Parameters({@Parameter(name = "actorId", description = "Exact actor filter"),
            @Parameter(name = "resourceType", description = "Exact resource type"),
            @Parameter(name = "resourceId", description = "Exact resource ID, independently optional"),
            @Parameter(name = "eventType", description = "Exact event type"),
            @Parameter(name = "from", description = "Inclusive ISO-8601 occurrence instant"),
            @Parameter(name = "to", description = "Exclusive ISO-8601 occurrence instant"),
            @Parameter(name = "limit", description = "1-200, default 50"),
            @Parameter(name = "cursor", description = "Signed opaque continuation token"),
            @Parameter(name = "includeArchived", description = "true/false, default false. true additionally requires AUDIT_LIFECYCLE")})
    public EventPage events(@Parameter(hidden = true) @RequestParam MultiValueMap<String, String> parameters,
            @Parameter(hidden = true) Authentication authentication) {
        EventQuery query = EventQuery.parse(parameters);
        archivePermission(query.includeArchived(), authentication);
        return queries.query(query);
    }

    @GetMapping("/events/{eventId}")
    @Operation(summary = "Get an event", description = "Requires AUDIT_READ; 404 if absent, 503 if integrity/key availability fails.")
    @Parameter(name = "includeArchived", description = "true additionally requires AUDIT_LIFECYCLE",
            schema = @io.swagger.v3.oas.annotations.media.Schema(type = "boolean", defaultValue = "false"))
    public EventView event(@PathVariable String eventId, @Parameter(hidden = true) @RequestParam MultiValueMap<String, String> parameters,
            @Parameter(hidden = true) Authentication authentication) {
        if (!java.util.Set.of("includeArchived").containsAll(parameters.keySet())) {
            throw new InvalidEventException("Lookup only accepts includeArchived");
        }
        boolean includeArchived = EventQuery.archived(parameters);
        archivePermission(includeArchived, authentication);
        UUID id;
        try {
            id = UUID.fromString(eventId);
            if (!id.toString().equalsIgnoreCase(eventId)) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new InvalidEventException("eventId must be a UUID");
        }
        return queries.find(id, includeArchived);
    }

    @GetMapping("/verify")
    @Operation(summary = "Verify the full chain", description = "Requires AUDIT_VERIFY. No filters. "
            + "200 reports intact or broken with first inconsistency; execution failures are 503, never intact=true.")
    public VerificationResult verify(@Parameter(hidden = true) @RequestParam MultiValueMap<String, String> parameters) {
        if (!parameters.isEmpty()) {
            throw new InvalidEventException("Verification does not accept filters");
        }
        return verification.verify();
    }

    private void archivePermission(boolean includeArchived, Authentication authentication) {
        if (includeArchived && (authentication == null || authentication.getAuthorities().stream()
                .noneMatch(authority -> authority.getAuthority().equals("AUDIT_LIFECYCLE")))) {
            throw new AccessDeniedException("Archived reads require lifecycle permission");
        }
    }
}
