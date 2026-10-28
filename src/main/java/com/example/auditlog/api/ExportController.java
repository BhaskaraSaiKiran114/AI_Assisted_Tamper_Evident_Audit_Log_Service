package com.example.auditlog.api;

import com.example.auditlog.security.ResourceScope;

import com.example.auditlog.export.ExportFilter;
import com.example.auditlog.export.ExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExportController {

    private final ExportService service;

    public ExportController(ExportService service) {
        this.service = service;
    }

    @GetMapping(value = "/audit/exports", produces = "application/zip")
    @Operation(summary = "Export signed protected events and chain proof", description = "Requires AUDIT_EXPORT. "
            + "actorId or resourceId required; combinations use AND. includeArchived=true additionally requires AUDIT_LIFECYCLE. "
            + "No field keys or decrypted protected values. Independent trusted key required for verification.",
            parameters = {@Parameter(name = "actorId", in = ParameterIn.QUERY),
                @Parameter(name = "resourceId", in = ParameterIn.QUERY), @Parameter(name = "resourceType", in = ParameterIn.QUERY),
                @Parameter(name = "includeArchived", in = ParameterIn.QUERY, schema = @Schema(type = "boolean", defaultValue = "false"))},
            responses = @ApiResponse(responseCode = "200", description = "Complete signed ZIP bundle",
                    content = @Content(mediaType = "application/zip", schema = @Schema(type = "string", format = "binary"))))
    public ResponseEntity<byte[]> export(@Parameter(hidden = true) @RequestParam MultiValueMap<String, String> parameters,
            @Parameter(hidden = true) Authentication authentication) {
        ExportFilter filter = ExportFilter.parse(parameters);
        if (filter.includeArchived() && authentication.getAuthorities().stream()
                .noneMatch(authority -> authority.getAuthority().equals("AUDIT_LIFECYCLE"))) {
            throw new AccessDeniedException("Archived export requires lifecycle permission");
        }
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit-export.zip\"")
                .body(service.export(filter, ResourceScope.from(authentication)));
    }
}
