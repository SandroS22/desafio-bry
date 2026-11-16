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
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Fluxo completo de ponta a ponta: assinar doc.txt via /signature (com pkcs12 e senha) e verificar via /verify/")
    void testEndToEndSignAndVerify() throws Exception {
        // Carrega o arquivo doc.txt real
        ClassPathResource docResource = new ClassPathResource("arquivos/doc.txt");
        byte[] docBytes = docResource.getInputStream().readAllBytes();

        // Carrega o arquivo PKCS#12 real
        ClassPathResource pfxResource = new ClassPathResource("pkcs12/certificado_teste_hub.pfx");
        byte[] pfxBytes = pfxResource.getInputStream().readAllBytes();

        MockMultipartFile signRequestFile = new MockMultipartFile(
                "file",
                "doc.txt",
                "text/plain",
                docBytes
        );
        MockMultipartFile pkcs12File = new MockMultipartFile(
                "pkcs12",
                "certificado.pfx",
                "application/x-pkcs12",
                pfxBytes
        );

        // 1. Chama /signature passando file, pkcs12 e password
        MvcResult signResult = mockMvc.perform(multipart("/signature/")
                        .file(signRequestFile)
                        .file(pkcs12File)
                        .param("password", "bry123456"))
                .andExpect(status().isOk())
                .andReturn();

        String base64Signature = signResult.getResponse().getContentAsString();
        assertNotNull(base64Signature);

        // Verifica que o arquivo binario assinatura.p7s foi gerado em disco
        Path p7sPath = Path.of("assinatura.p7s");
        assertTrue(Files.exists(p7sPath));
        byte[] p7sBytes = Files.readAllBytes(p7sPath);

        // 2. Chama /verify/ com o arquivo binario assinatura.p7s
        MockMultipartFile verifyRequestFile = new MockMultipartFile(
                "file",
                "assinatura.p7s",
                "application/pkcs7-signature",
                p7sBytes
        );

        // Hash SHA-512 esperado para doc.txt:
        // dc1a7de77c59a29f366a4b154b03ad7d99013e36e08beb50d976358bea7b045884fe72111b27cf7d6302916b2691ac7696c1637e1ab44584d8d6613825149e35
        mockMvc.perform(multipart("/verify/").file(verifyRequestFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.Status", is("VALIDO")))
                .andExpect(jsonPath("$.Infos.hashDocumento", is("dc1a7de77c59a29f366a4b154b03ad7d99013e36e08beb50d976358bea7b045884fe72111b27cf7d6302916b2691ac7696c1637e1ab44584d8d6613825149e35")))
                .andExpect(jsonPath("$.Infos.algoritmoHash", is("SHA512")))
                .andExpect(jsonPath("$.Infos.nomeSignatario").exists())
                .andExpect(jsonPath("$.Infos.dataAssinatura").exists());
    }

    @Test
    @DisplayName("Integracao: /signature/ sem upload deve ler doc.txt padrao e /verify sem upload deve ler assinatura.p7s gerada")
    void testSignAndVerifyWithoutUpload() throws Exception {
        // 1. Chama /signature/ sem parametro de arquivo (fallback doc.txt e pkcs12 do classpath)
        mockMvc.perform(multipart("/signature/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.notNullValue()));

        assertTrue(Files.exists(Path.of("assinatura.p7s")));

        // 2. Chama /verify sem upload (fallback assinatura.p7s em disco)
        mockMvc.perform(multipart("/verify/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.Status", is("VALIDO")))
                .andExpect(jsonPath("$.Infos.hashDocumento", is("dc1a7de77c59a29f366a4b154b03ad7d99013e36e08beb50d976358bea7b045884fe72111b27cf7d6302916b2691ac7696c1637e1ab44584d8d6613825149e35")));
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

        mockMvc.perform(multipart("/verify/").file(verifyRequestFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.Status", is("INVALIDO")))
                .andExpect(jsonPath("$.Infos").doesNotExist());
    }
}
