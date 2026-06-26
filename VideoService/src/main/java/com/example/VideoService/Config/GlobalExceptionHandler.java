package com.example.VideoService.Config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

/**
 * Handles upload size limit violations globally.
 * Converts Spring's default 500 error into a user-friendly 413 Payload Too Large response.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of(
                    "error", "File too large. Maximum video size is 100 MB and maximum thumbnail size is 10 MB.",
                    "details", ex.getMessage() != null ? ex.getMessage() : "Upload size limit exceeded"
                ));
    }
}
