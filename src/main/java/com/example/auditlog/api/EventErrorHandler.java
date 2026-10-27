package com.example.auditlog.api;

import com.example.auditlog.audit.InvalidEventException;
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
}
