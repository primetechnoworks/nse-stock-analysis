package com.nse.stockfetcher.exception;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(StockNotFoundException.class)
    public ResponseEntity<?> stockNotFound(StockNotFoundException e) {
        return ResponseEntity.status(404)
                .body(Map.of("error", "Stock not found for the given symbol", "message", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> globalException(Exception e) {
        return ResponseEntity.status(500)
                .body(Map.of("error", "An unexpected error occurred", "message", e.getMessage()));
    }

}
