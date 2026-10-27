package com.example.auditlog.api;

import java.io.IOException;
import java.net.URI;

import com.example.auditlog.audit.AppendReceipt;
import com.example.auditlog.audit.AuditIngestionService;
import com.example.auditlog.audit.EventParser;
import com.example.auditlog.audit.InvalidEventException;
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
    public ResponseEntity<AppendReceipt> append(HttpServletRequest request) throws IOException {
        if (request.getContentLengthLong() > EventParser.MAX_BYTES) {
            throw new InvalidEventException("Event body exceeds 256 KiB", 413);
        }
        AppendReceipt receipt = ingestion.append(request.getInputStream().readNBytes(EventParser.MAX_BYTES + 1));
        return ResponseEntity.created(URI.create("/audit/events/" + receipt.eventId())).body(receipt);
    }
}
