package com.github.sandros22.desafio_bry.controller;

import com.github.sandros22.desafio_bry.crypto.CmsSigner;
import com.github.sandros22.desafio_bry.crypto.CmsVerifier;
import com.github.sandros22.desafio_bry.dto.SignatureInfos;
import com.github.sandros22.desafio_bry.dto.VerifyResponse;
import com.github.sandros22.desafio_bry.exception.GlobalExceptionHandler;
import org.bouncycastle.cms.CMSException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = Controller.class)
@Import(GlobalExceptionHandler.class)
class ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CmsSigner signer;

    @MockBean
    private CmsVerifier verifier;

    @Test
    @DisplayName("POST /sign com arquivo válido deve retornar 200 OK e a assinatura em Base64")
    void testSignFile_Success() throws Exception {
        byte[] originalContent = "Conteúdo para assinatura".getBytes(StandardCharsets.UTF_8);
        byte[] mockSignedBytes = "Mock Signed CMS Bytes".getBytes(StandardCharsets.UTF_8);
        String expectedBase64 = Base64.getEncoder().encodeToString(mockSignedBytes);

        when(signer.signCmsAttached(originalContent)).thenReturn(mockSignedBytes);

        MockMultipartFile file = new MockMultipartFile("file", "doc.txt", "text/plain", originalContent);

        mockMvc.perform(multipart("/sign").file(file))
                .andExpect(status().isOk())
                .andExpect(content().string(expectedBase64));
    }

    @Test
    @DisplayName("POST /sign com arquivo vazio deve retornar 400 Bad Request")
    void testSignFile_EmptyFile_ReturnsBadRequest() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

        mockMvc.perform(multipart("/sign").file(emptyFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("O arquivo para assinatura não pode estar vazio"));
    }

    @Test
    @DisplayName("POST /sign sem a parte 'file' deve retornar 400 Bad Request")
    void testSignFile_MissingFilePart_ReturnsBadRequest() throws Exception {
        mockMvc.perform(multipart("/sign"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").exists());
    }

    @Test
    @DisplayName("POST /sign quando signer lança exceção deve retornar 500 Internal Server Error")
    void testSignFile_SignerThrowsException_ReturnsInternalServerError() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "doc.txt", "text/plain", "conteudo".getBytes(StandardCharsets.UTF_8));
        when(signer.signCmsAttached(any())).thenThrow(new RuntimeException("Falha no KeyStore"));

        mockMvc.perform(multipart("/sign").file(file))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.erro").value("Falha no processamento criptográfico: Falha no KeyStore"));
    }

    @Test
    @DisplayName("POST /verify com arquivo válido deve retornar 200 OK e VerifyResponse com status VALIDO")
    void testVerifySignature_Success() throws Exception {
        byte[] fileBytes = "base64-content".getBytes(StandardCharsets.UTF_8);
        SignatureInfos infos = new SignatureInfos(
                "Signatário de Teste",
                "2026-11-15T12:00:00Z",
                "abcd1234efgh5678",
                "SHA512"
        );
        VerifyResponse expectedResponse = new VerifyResponse("VALIDO", infos);

        when(verifier.verifyBase64Cms(fileBytes)).thenReturn(expectedResponse);

        MockMultipartFile file = new MockMultipartFile("file", "assinatura.txt", "text/plain", fileBytes);

        mockMvc.perform(multipart("/verify").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.Status").value("VALIDO"))
                .andExpect(jsonPath("$.Infos.nomeSignatario").value("Signatário de Teste"))
                .andExpect(jsonPath("$.Infos.dataAssinatura").value("2026-11-15T12:00:00Z"))
                .andExpect(jsonPath("$.Infos.hashDocumento").value("abcd1234efgh5678"))
                .andExpect(jsonPath("$.Infos.algoritmoHash").value("SHA512"));
    }

    @Test
    @DisplayName("POST /verify quando verifier lança CMSException deve retornar 200 OK com status INVALIDO")
    void testVerifySignature_CMSException_ReturnsInvalido() throws Exception {
        byte[] fileBytes = "corrupted-cms".getBytes(StandardCharsets.UTF_8);
        when(verifier.verifyBase64Cms(fileBytes)).thenThrow(new CMSException("CMS corrompido"));

        MockMultipartFile file = new MockMultipartFile("file", "assinatura.txt", "text/plain", fileBytes);

        mockMvc.perform(multipart("/verify").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.Status").value("INVALIDO"))
                .andExpect(jsonPath("$.Infos").doesNotExist());
    }

    @Test
    @DisplayName("POST /verify quando verifier lança IllegalArgumentException deve retornar 400 Bad Request")
    void testVerifySignature_IllegalArgumentException_ReturnsBadRequest() throws Exception {
        byte[] fileBytes = "invalid-base64".getBytes(StandardCharsets.UTF_8);
        when(verifier.verifyBase64Cms(fileBytes)).thenThrow(new IllegalArgumentException("Base64 inválido"));

        MockMultipartFile file = new MockMultipartFile("file", "assinatura.txt", "text/plain", fileBytes);

        mockMvc.perform(multipart("/verify").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Base64 inválido"));
    }

    @Test
    @DisplayName("POST /verify quando verifier lança exceção inesperada deve retornar 500 Internal Server Error")
    void testVerifySignature_UnexpectedException_ReturnsInternalServerError() throws Exception {
        byte[] fileBytes = "content".getBytes(StandardCharsets.UTF_8);
        when(verifier.verifyBase64Cms(fileBytes)).thenThrow(new RuntimeException("Erro inesperado"));

        MockMultipartFile file = new MockMultipartFile("file", "assinatura.txt", "text/plain", fileBytes);

        mockMvc.perform(multipart("/verify").file(file))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.erro").value("Falha no processamento criptográfico: Erro inesperado"));
    }

    @Test
    @DisplayName("POST /verify sem a parte 'file' deve retornar 400 Bad Request")
    void testVerifySignature_MissingFilePart_ReturnsBadRequest() throws Exception {
        mockMvc.perform(multipart("/verify"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").exists());
    }
}
