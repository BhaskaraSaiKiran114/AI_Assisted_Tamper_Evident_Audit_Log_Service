package com.example.auditlog.api;

import java.io.IOException;
import java.net.URI;

import com.example.auditlog.audit.AppendReceipt;
import com.example.auditlog.audit.AuditIngestionService;
import com.example.auditlog.audit.EventParser;
import com.example.auditlog.audit.InvalidEventException;
import com.example.auditlog.audit.EventInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/audit/events")
public class AuditWriteController {

    private final AuditIngestionService ingestion;

    public AuditWriteController(AuditIngestionService ingestion) {
        this.ingestion = ingestion;
    }

    @PostMapping(consumes = "application/json", produces = "application/json")
    @Operation(summary = "Append one event", description = "Requires AUDIT_WRITE. No request idempotency guarantee.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = EventInput.class))))
    @ApiResponses({@ApiResponse(responseCode = "201", description = "Committed event receipt",
                    content = @Content(schema = @Schema(implementation = AppendReceipt.class))),
            @ApiResponse(responseCode = "400", description = "Invalid event"),
            @ApiResponse(responseCode = "401", description = "Invalid or absent token"),
            @ApiResponse(responseCode = "403", description = "Missing permission"),
            @ApiResponse(responseCode = "413", description = "Request exceeds 256 KiB"),
            @ApiResponse(responseCode = "503", description = "Storage unavailable")})
    public ResponseEntity<AppendReceipt> append(HttpServletRequest request) throws IOException {
        if (request.getContentLengthLong() > EventParser.MAX_BYTES) {
            throw new InvalidEventException("Event body exceeds 256 KiB", 413);
        }
        AppendReceipt receipt = ingestion.append(request.getInputStream().readNBytes(EventParser.MAX_BYTES + 1));
        return ResponseEntity.created(URI.create("/audit/events/" + receipt.eventId())).body(receipt);
    }
}
