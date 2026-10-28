package com.example.auditlog.api;

import com.example.auditlog.security.ResourceScope;

import com.example.auditlog.report.AccessReportQuery;
import com.example.auditlog.report.AccessReportService;
import com.example.auditlog.report.AccessReportService.Report;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AccessReportController {

    private final AccessReportService service;

    public AccessReportController(AccessReportService service) {
        this.service = service;
    }

    @GetMapping("/audit/compliance/access-report")
    @Operation(summary = "Report recorded client-account access attempts", description = "Requires AUDIT_COMPLIANCE; "
            + "archive opt-in additionally requires AUDIT_LIFECYCLE. Metadata only, no payload or decryption. "
            + "CLIENT_ACCOUNT READ/UPDATE/EXPORT with plaintext outcome SUCCESS/DENIED/FAILURE. "
            + "Unsupported/protected outcomes are excluded and counted. Does not certify regulatory compliance or upstream completeness.",
            parameters = {@Parameter(name = "from", in = ParameterIn.QUERY, required = true, schema = @Schema(type = "string", format = "date-time")),
                @Parameter(name = "to", in = ParameterIn.QUERY, required = true, schema = @Schema(type = "string", format = "date-time")),
                @Parameter(name = "accountId", in = ParameterIn.QUERY), @Parameter(name = "actorId", in = ParameterIn.QUERY),
                @Parameter(name = "action", in = ParameterIn.QUERY, schema = @Schema(allowableValues = {"READ", "UPDATE", "EXPORT"})),
                @Parameter(name = "outcome", in = ParameterIn.QUERY, schema = @Schema(allowableValues = {"SUCCESS", "DENIED", "FAILURE"})),
                @Parameter(name = "limit", in = ParameterIn.QUERY, schema = @Schema(type = "integer", minimum = "1", maximum = "200", defaultValue = "50")),
                @Parameter(name = "cursor", in = ParameterIn.QUERY),
                @Parameter(name = "includeArchived", in = ParameterIn.QUERY, schema = @Schema(type = "boolean", defaultValue = "false"))})
    public Report report(@Parameter(hidden = true) @RequestParam MultiValueMap<String, String> parameters,
            @Parameter(hidden = true) Authentication authentication) {
        var query = AccessReportQuery.parse(parameters);
        if (query.includeArchived() && authentication.getAuthorities().stream()
                .noneMatch(authority -> authority.getAuthority().equals("AUDIT_LIFECYCLE"))) {
            throw new AccessDeniedException("Archived report requires lifecycle permission");
        }
        return service.report(query, ResourceScope.from(authentication));
    }
}
