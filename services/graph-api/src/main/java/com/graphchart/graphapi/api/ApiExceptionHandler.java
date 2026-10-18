package com.graphchart.graphapi.api;

import com.graphchart.graphapi.service.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    Map<String, Object> notFound(NotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, Object> badRequest(Exception exception) {
        String message = exception instanceof MethodArgumentNotValidException validation
                ? validation.getBindingResult().getFieldError() == null
                ? "Request validation failed"
                : validation.getBindingResult().getFieldError().getDefaultMessage()
                : exception.getMessage();
        return error(HttpStatus.BAD_REQUEST, message);
    }

    private Map<String, Object> error(HttpStatus status, String message) {
        return Map.of("timestamp", OffsetDateTime.now(), "status", status.value(), "error", message);
    }
}
