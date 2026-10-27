package com.example.auditlog.api;

import com.example.auditlog.audit.InvalidEventException;
import com.example.auditlog.audit.AuditQueryException;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class EventErrorHandler {

    @ExceptionHandler(InvalidEventException.class)
    ProblemDetail invalidEvent(InvalidEventException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(exception.status()), exception.getMessage());
    }

    @ExceptionHandler(AuditQueryException.class)
    ProblemDetail queryFailure(AuditQueryException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(exception.status()), exception.getMessage());
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    ProblemDetail storageFailure(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(503), "Audit storage temporarily unavailable");
    }
}
