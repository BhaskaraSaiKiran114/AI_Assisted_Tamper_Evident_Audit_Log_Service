package com.example.auditlog.audit;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.example.auditlog.persistence.AuditAppender;
import org.springframework.stereotype.Service;

@Service
public class AuditIngestionService {

    private final EventParser parser;
    private final PayloadProtector protector;
    private final AuditAppender appender;

    public AuditIngestionService(EventParser parser, PayloadProtector protector, AuditAppender appender) {
        this.parser = parser;
        this.protector = protector;
        this.appender = appender;
    }

    public AppendReceipt append(byte[] request) {
        EventInput input = parser.parse(request);
        UUID id = UUID.randomUUID();
        Instant ingested = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return appender.append(id, ingested, input, protector.protect(id, input.payload()));
    }
}
