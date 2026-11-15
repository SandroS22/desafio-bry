package com.github.sandros22.desafio_bry.exception;

import com.github.sandros22.desafio_bry.dto.VerifyResponse;
import org.bouncycastle.cms.CMSException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({IllegalArgumentException.class, MissingServletRequestPartException.class})
    public ResponseEntity<Map<String, String>> handleBadRequest(Exception ex) {
        return ResponseEntity.badRequest().body(Map.of("erro", ex.getMessage()));
    }

    @ExceptionHandler(CMSException.class)
    public ResponseEntity<VerifyResponse> handleInvalidCms() {
        return ResponseEntity.ok(new VerifyResponse("INVALIDO", null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        return ResponseEntity
                .internalServerError().body(Map.of("erro", "Falha no processamento criptográfico: " + ex.getMessage()));
    }
}