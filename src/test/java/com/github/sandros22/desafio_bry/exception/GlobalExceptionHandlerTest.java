package com.github.sandros22.desafio_bry.exception;

import com.github.sandros22.desafio_bry.dto.VerifyResponse;
import org.bouncycastle.cms.CMSException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("Deve retornar 400 Bad Request ao capturar IllegalArgumentException")
    void testHandleIllegalArgumentException() {
        IllegalArgumentException ex = new IllegalArgumentException("Arquivo vazio não permitido");
        ResponseEntity<Map<String, String>> response = handler.handleBadRequest(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Arquivo vazio não permitido", response.getBody().get("erro"));
    }

    @Test
    @DisplayName("Deve retornar 400 Bad Request ao capturar MissingServletRequestPartException")
    void testHandleMissingServletRequestPartException() {
        MissingServletRequestPartException ex = new MissingServletRequestPartException("file");
        ResponseEntity<Map<String, String>> response = handler.handleBadRequest(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().get("erro").contains("file"));
    }

    @Test
    @DisplayName("Deve retornar 200 OK com status INVALIDO ao capturar CMSException")
    void testHandleCMSException() {
        ResponseEntity<VerifyResponse> response = handler.handleInvalidCms();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INVALIDO", response.getBody().Status());
        assertNull(response.getBody().Infos());
    }

    @Test
    @DisplayName("Deve retornar 500 Internal Server Error com mensagem customizada para outras exceções")
    void testHandleUnexpectedException() {
        RuntimeException ex = new RuntimeException("Erro inesperado no servidor");
        ResponseEntity<Map<String, String>> response = handler.handleUnexpected(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Falha no processamento criptográfico: Erro inesperado no servidor", response.getBody().get("erro"));
    }
}
