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

    // Retorna HTTP 400 para parâmetros ausentes ou argumentos inválidos enviados na requisição
    @ExceptionHandler({IllegalArgumentException.class, MissingServletRequestPartException.class})
    public ResponseEntity<Map<String, String>> handleBadRequest(Exception ex) {
        return ResponseEntity.badRequest().body(Map.of("erro", ex.getMessage()));
    }

    // Retorna HTTP 200 com status INVALIDO quando a estrutura CMS estiver corrompida
    @ExceptionHandler(CMSException.class)
    public ResponseEntity<VerifyResponse> handleInvalidCms() {
        return ResponseEntity.ok(new VerifyResponse("INVALIDO", null));
    }

    // Captura falhas internas inesperadas e retorna erro HTTP 500 padronizado
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        return ResponseEntity
                .internalServerError().body(Map.of("erro", "Falha no processamento criptográfico: " + ex.getMessage()));
    }
}