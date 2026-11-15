package com.github.sandros22.desafio_bry.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Fluxo completo de ponta a ponta: assinar doc.txt via /sign e verificar via /verify")
    void testEndToEndSignAndVerify() throws Exception {
        // Carrega o arquivo doc.txt real
        ClassPathResource docResource = new ClassPathResource("arquivos/doc.txt");
        byte[] docBytes = docResource.getInputStream().readAllBytes();

        MockMultipartFile signRequestFile = new MockMultipartFile(
                "file",
                "doc.txt",
                "text/plain",
                docBytes
        );

        // 1. Chama /sign
        MvcResult signResult = mockMvc.perform(multipart("/sign").file(signRequestFile))
                .andExpect(status().isOk())
                .andReturn();

        String base64Signature = signResult.getResponse().getContentAsString();
        assertNotNull(base64Signature);

        // 2. Chama /verify com a assinatura obtida
        MockMultipartFile verifyRequestFile = new MockMultipartFile(
                "file",
                "assinatura.txt",
                "text/plain",
                base64Signature.getBytes(StandardCharsets.UTF_8)
        );

        // Hash SHA-512 esperado para doc.txt:
        // dc1a7de77c59a29f366a4b154b03ad7d99013e36e08beb50d976358bea7b045884fe72111b27cf7d6302916b2691ac7696c1637e1ab44584d8d6613825149e35
        mockMvc.perform(multipart("/verify").file(verifyRequestFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.Status", is("VALIDO")))
                .andExpect(jsonPath("$.Infos.hashDocumento", is("dc1a7de77c59a29f366a4b154b03ad7d99013e36e08beb50d976358bea7b045884fe72111b27cf7d6302916b2691ac7696c1637e1ab44584d8d6613825149e35")))
                .andExpect(jsonPath("$.Infos.algoritmoHash", is("SHA512")))
                .andExpect(jsonPath("$.Infos.nomeSignatario").exists())
                .andExpect(jsonPath("$.Infos.dataAssinatura").exists());
    }

    @Test
    @DisplayName("Integracao: /verify com CMS corrompido deve retornar 200 OK com Status INVALIDO")
    void testVerifyCorruptedCmsReturnsInvalido() throws Exception {
        // Envia bytes que formam um base64 válido mas CMS corrompido
        byte[] invalidCmsBytes = "NaoEhUmCmsValidoApenasBase64".getBytes(StandardCharsets.UTF_8);

        MockMultipartFile verifyRequestFile = new MockMultipartFile(
                "file",
                "invalido.txt",
                "text/plain",
                invalidCmsBytes
        );

        mockMvc.perform(multipart("/verify").file(verifyRequestFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.Status", is("INVALIDO")))
                .andExpect(jsonPath("$.Infos").doesNotExist());
    }

    @Test
    @DisplayName("Integracao: /sign com arquivo vazio deve retornar 400 Bad Request")
    void testSignEmptyFileReturnsBadRequest() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.txt",
                "text/plain",
                new byte[0]
        );

        mockMvc.perform(multipart("/sign").file(emptyFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro", is("O arquivo para assinatura não pode estar vazio")));
    }
}
