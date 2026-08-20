package com.churchsong.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(
            IllegalArgumentException exception) {
        return ResponseEntity.status(
                        HttpStatus.BAD_REQUEST)
                .body(
                        Map.of(
                                "message",
                                exception.getMessage()));
    }

    @ExceptionHandler(CannotAcquireLockException.class)
    public ResponseEntity<Map<String, String>> handleCannotAcquireLock(
            CannotAcquireLockException exception) {
        return ResponseEntity.status(
                        HttpStatus.CONFLICT)
                .body(
                        Map.of(
                                "message",
                                "The song library is busy right now. Please try again in a moment."
                        ));
    }
}
