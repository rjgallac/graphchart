package com.graphchart.telemetry.api;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(DuplicateKeyException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    Map<String, Object> duplicate() { return error(HttpStatus.CONFLICT, "eventId already exists"); }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, Object> badRequest(Exception exception) {
        String message = exception instanceof MethodArgumentNotValidException validation && validation.getBindingResult().getFieldError() != null
                ? validation.getBindingResult().getFieldError().getDefaultMessage() : exception.getMessage();
        return error(HttpStatus.BAD_REQUEST, message);
    }

    private Map<String, Object> error(HttpStatus status, String message) {
        return Map.of("timestamp", OffsetDateTime.now(), "status", status.value(), "error", message);
    }
}
