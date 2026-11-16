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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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
    @DisplayName("POST /signature/ com arquivo, pkcs12 e password deve retornar 200 OK e a assinatura em Base64")
    void testSignature_SuccessWithParameters() throws Exception {
        byte[] originalContent = "Conteúdo para assinatura".getBytes(StandardCharsets.UTF_8);
        byte[] pfxBytes = "Mock PKCS12 bytes".getBytes(StandardCharsets.UTF_8);
        byte[] mockSignedBytes = "Mock Signed CMS Bytes".getBytes(StandardCharsets.UTF_8);
        String expectedBase64 = Base64.getEncoder().encodeToString(mockSignedBytes);

        when(signer.signCmsAttached(eq(originalContent), eq(pfxBytes), eq("minhasenha"))).thenReturn(mockSignedBytes);

        MockMultipartFile file = new MockMultipartFile("file", "doc.txt", "text/plain", originalContent);
        MockMultipartFile pkcs12 = new MockMultipartFile("pkcs12", "cert.pfx", "application/x-pkcs12", pfxBytes);

        mockMvc.perform(multipart("/signature/")
                        .file(file)
                        .file(pkcs12)
                        .param("password", "minhasenha"))
                .andExpect(status().isOk())
                .andExpect(content().string(expectedBase64));

        verify(signer).signCmsAttached(originalContent, pfxBytes, "minhasenha");
    }

    @Test
    @DisplayName("POST /signature/ (com barra final) deve ser aceito e retornar 200 OK")
    void testSignature_TrailingSlash() throws Exception {
        byte[] originalContent = "Conteúdo para assinatura".getBytes(StandardCharsets.UTF_8);
        byte[] mockSignedBytes = "Mock Signed CMS Bytes".getBytes(StandardCharsets.UTF_8);

        when(signer.signCmsAttached(eq(originalContent), any(), any())).thenReturn(mockSignedBytes);

        MockMultipartFile file = new MockMultipartFile("file", "doc.txt", "text/plain", originalContent);

        mockMvc.perform(multipart("/signature/").file(file))
                .andExpect(status().isOk())
                .andExpect(content().string(Base64.getEncoder().encodeToString(mockSignedBytes)));
    }

    @Test
    @DisplayName("POST /signature/ com arquivo vazio deve usar o documento padrão doc.txt")
    void testSignature_EmptyFile_UsesDefaultDoc() throws Exception {
        byte[] defaultDocBytes = "Conteudo padrao de doc.txt".getBytes(StandardCharsets.UTF_8);
        byte[] mockSignedBytes = "Mock Signed Default".getBytes(StandardCharsets.UTF_8);

        when(signer.loadDefaultDoc()).thenReturn(defaultDocBytes);
        when(signer.signCmsAttached(eq(defaultDocBytes), any(), any())).thenReturn(mockSignedBytes);

        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

        mockMvc.perform(multipart("/signature/").file(emptyFile))
                .andExpect(status().isOk())
                .andExpect(content().string(Base64.getEncoder().encodeToString(mockSignedBytes)));

        verify(signer).loadDefaultDoc();
    }

    @Test
    @DisplayName("POST /signature/ sem a parte 'file' deve usar o documento padrão doc.txt como fallback")
    void testSignature_MissingFilePart_UsesDefaultDoc() throws Exception {
        byte[] defaultDocBytes = "Conteudo padrao de doc.txt".getBytes(StandardCharsets.UTF_8);
        byte[] mockSignedBytes = "Mock Signed Default".getBytes(StandardCharsets.UTF_8);

        when(signer.loadDefaultDoc()).thenReturn(defaultDocBytes);
        when(signer.signCmsAttached(eq(defaultDocBytes), any(), any())).thenReturn(mockSignedBytes);

        mockMvc.perform(multipart("/signature/"))
                .andExpect(status().isOk())
                .andExpect(content().string(Base64.getEncoder().encodeToString(mockSignedBytes)));

        verify(signer).loadDefaultDoc();
    }

    @Test
    @DisplayName("POST /signature/ quando signer lança exceção deve retornar 500 Internal Server Error")
    void testSignature_SignerThrowsException_ReturnsInternalServerError() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "doc.txt", "text/plain", "conteudo".getBytes(StandardCharsets.UTF_8));
        when(signer.signCmsAttached(any(), any(), any())).thenThrow(new RuntimeException("Falha no KeyStore"));

        mockMvc.perform(multipart("/signature/").file(file))
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

        MockMultipartFile file = new MockMultipartFile("file", "assinatura.p7s", "application/octet-stream", fileBytes);

        mockMvc.perform(multipart("/verify/").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.Status").value("VALIDO"))
                .andExpect(jsonPath("$.Infos.nomeSignatario").value("Signatário de Teste"))
                .andExpect(jsonPath("$.Infos.dataAssinatura").value("2026-11-15T12:00:00Z"))
                .andExpect(jsonPath("$.Infos.hashDocumento").value("abcd1234efgh5678"))
                .andExpect(jsonPath("$.Infos.algoritmoHash").value("SHA512"));
    }

    @Test
    @DisplayName("POST /verify/ sem arquivo deve ler assinatura.p7s se existir em disco")
    void testVerifySignature_FallbackToP7sOnDisk() throws Exception {
        Path p7sPath = Path.of("assinatura.p7s");
        byte[] p7sBytes = "p7s-dummy-bytes".getBytes(StandardCharsets.UTF_8);
        Files.write(p7sPath, p7sBytes);

        try {
            SignatureInfos infos = new SignatureInfos("Test", "2026-11-15T12:00:00Z", "hash", "SHA512");
            when(verifier.verifyBase64Cms(any())).thenReturn(new VerifyResponse("VALIDO", infos));

            mockMvc.perform(multipart("/verify/"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.Status").value("VALIDO"));
        } finally {
            Files.deleteIfExists(p7sPath);
        }
    }

    @Test
    @DisplayName("POST /verify/ sem arquivo quando assinatura.p7s não existe deve retornar 400 Bad Request")
    void testVerifySignature_MissingFilePart_NoP7sOnDisk_ReturnsBadRequest() throws Exception {
        Path p7sPath = Path.of("assinatura.p7s");
        Files.deleteIfExists(p7sPath);

        mockMvc.perform(multipart("/verify/"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("O arquivo para verificação não pode estar vazio"));
    }

    @Test
    @DisplayName("POST /verify/ quando verifier lança CMSException deve retornar 200 OK com status INVALIDO")
    void testVerifySignature_CMSException_ReturnsInvalido() throws Exception {
        byte[] fileBytes = "corrupted-cms".getBytes(StandardCharsets.UTF_8);
        when(verifier.verifyBase64Cms(fileBytes)).thenThrow(new CMSException("CMS corrompido"));

        MockMultipartFile file = new MockMultipartFile("file", "assinatura.p7s", "text/plain", fileBytes);

        mockMvc.perform(multipart("/verify/").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.Status").value("INVALIDO"))
                .andExpect(jsonPath("$.Infos").doesNotExist());
    }

    @Test
    @DisplayName("POST /verify/ quando verifier lança IllegalArgumentException deve retornar 400 Bad Request")
    void testVerifySignature_IllegalArgumentException_ReturnsBadRequest() throws Exception {
        byte[] fileBytes = "invalid-base64".getBytes(StandardCharsets.UTF_8);
        when(verifier.verifyBase64Cms(fileBytes)).thenThrow(new IllegalArgumentException("Base64 inválido"));

        MockMultipartFile file = new MockMultipartFile("file", "assinatura.p7s", "text/plain", fileBytes);

        mockMvc.perform(multipart("/verify/").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Base64 inválido"));
    }

    @Test
    @DisplayName("POST /verify/ quando verifier lança exceção inesperada deve retornar 500 Internal Server Error")
    void testVerifySignature_UnexpectedException_ReturnsInternalServerError() throws Exception {
        byte[] fileBytes = "content".getBytes(StandardCharsets.UTF_8);
        when(verifier.verifyBase64Cms(fileBytes)).thenThrow(new RuntimeException("Erro inesperado"));

        MockMultipartFile file = new MockMultipartFile("file", "assinatura.p7s", "text/plain", fileBytes);

        mockMvc.perform(multipart("/verify/").file(file))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.erro").value("Falha no processamento criptográfico: Erro inesperado"));
    }
}
