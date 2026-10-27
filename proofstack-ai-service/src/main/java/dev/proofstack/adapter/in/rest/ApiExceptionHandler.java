package dev.proofstack.adapter.in.rest;

import dev.proofstack.core.domain.content.ContentGenerationException;
import dev.proofstack.core.domain.knowledge.InvalidQuestionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** Maps failures to HTTP without leaking internals (model names, hosts, stack traces, questions). */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(InvalidQuestionException.class)
    ResponseEntity<Map<String, String>> invalidQuestion(InvalidQuestionException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, HttpMediaTypeNotSupportedException.class})
    ResponseEntity<Map<String, String>> badBody(Exception e) {
        return error(HttpStatus.BAD_REQUEST, "send JSON like {\"question\": \"...\"}");
    }

    @ExceptionHandler(ContentGenerationException.class)
    ResponseEntity<Map<String, String>> generation(ContentGenerationException e) {
        // Server-side log keeps the kind and cause type for operators; the client gets a generic message.
        log.warn("assistant failure kind={} cause={}", e.kind(),
                e.getCause() == null ? "-" : e.getCause().getClass().getSimpleName());
        return error(HttpStatus.SERVICE_UNAVAILABLE, "the assistant is temporarily unavailable");
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
