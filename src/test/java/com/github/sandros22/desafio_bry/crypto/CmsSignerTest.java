package com.github.sandros22.desafio_bry.crypto;

import org.bouncycastle.cms.CMSSignedData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.util.Vector;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class CmsSignerTest {

    @Autowired
    private CmsSigner cmsSigner;

    @Test
    @DisplayName("Deve carregar o KeyStore PKCS12 com sucesso a partir do classpath")
    void testLoadKeyStoreSuccess() throws Exception {
        KeyStore keyStore = cmsSigner.loadKeyStore();
        assertNotNull(keyStore);
        assertTrue(keyStore.size() > 0);
    }

    @Test
    @DisplayName("Deve assinar dinamicamente com bytes PKCS#12 e senha enviados por parâmetro")
    void testSignCmsAttached_DynamicPkcs12() throws Exception {
        ClassPathResource pfxResource = new ClassPathResource("pkcs12/certificado_teste_hub.pfx");
        byte[] pfxBytes = pfxResource.getInputStream().readAllBytes();
        byte[] content = "Conteudo para teste dinamico".getBytes(StandardCharsets.UTF_8);

        byte[] signedBytes = cmsSigner.signCmsAttached(content, pfxBytes, "bry123456");
        assertNotNull(signedBytes);
        assertTrue(signedBytes.length > 0);

        CMSSignedData signedData = new CMSSignedData(signedBytes);
        assertNotNull(signedData.getSignedContent());
        assertArrayEquals(content, (byte[]) signedData.getSignedContent().getContent());
    }

    @Test
    @DisplayName("Deve assinar dados anexados e gravar o arquivo binário assinatura.p7s")
    void testSignCmsAttachedSuccess() throws Exception {
        byte[] content = "Conteudo de teste para assinatura digital".getBytes(StandardCharsets.UTF_8);

        byte[] signedBytes = cmsSigner.signCmsAttached(content);
        assertNotNull(signedBytes);
        assertTrue(signedBytes.length > 0);

        // Verifica integridade do arquivo binário assinatura.p7s gerado
        Path assinaturaPath = Path.of("assinatura.p7s");
        assertTrue(Files.exists(assinaturaPath));
        byte[] fileBytes = Files.readAllBytes(assinaturaPath);
        assertArrayEquals(signedBytes, fileBytes);

        // Valida que o CMS gerado contém o conteúdo anexado
        CMSSignedData signedData = new CMSSignedData(signedBytes);
        assertNotNull(signedData.getSignedContent());
        byte[] extractedContent = (byte[]) signedData.getSignedContent().getContent();
        assertArrayEquals(content, extractedContent);
        assertFalse(signedData.getSignerInfos().getSigners().isEmpty());
    }

    @Test
    @DisplayName("Deve carregar e assinar o arquivo padrão doc.txt em signDefaultDoc")
    void testSignDefaultDoc() throws Exception {
        byte[] defaultDoc = cmsSigner.loadDefaultDoc();
        assertNotNull(defaultDoc);
        assertTrue(defaultDoc.length > 0);

        byte[] signedBytes = cmsSigner.signDefaultDoc();
        assertNotNull(signedBytes);

        CMSSignedData signedData = new CMSSignedData(signedBytes);
        byte[] extractedContent = (byte[]) signedData.getSignedContent().getContent();
        assertArrayEquals(defaultDoc, extractedContent);
    }

    @Test
    @DisplayName("Deve resolver o alias configurado quando existir no KeyStore")
    void testResolveAlias_ConfiguredAliasPresent() throws Exception {
        KeyStore keyStore = cmsSigner.loadKeyStore();
        String actualAlias = keyStore.aliases().nextElement();

        CmsSigner signer = new CmsSigner();
        ReflectionTestUtils.setField(signer, "alias", actualAlias);
        ReflectionTestUtils.setField(signer, "password", "bry123456");

        String resolved = signer.resolveAlias(keyStore);
        assertEquals(actualAlias, resolved);
    }

    @Test
    @DisplayName("Deve resolver o alias fazendo trim de espaços em branco")
    void testResolveAlias_ConfiguredAliasWithWhitespace() throws Exception {
        KeyStore keyStore = cmsSigner.loadKeyStore();
        String actualAlias = keyStore.aliases().nextElement();

        CmsSigner signer = new CmsSigner();
        ReflectionTestUtils.setField(signer, "alias", "  " + actualAlias + "  ");
        ReflectionTestUtils.setField(signer, "password", "bry123456");

        String resolved = signer.resolveAlias(keyStore);
        assertEquals(actualAlias, resolved);
    }

    @Test
    @DisplayName("Deve resolver o primeiro alias de chave quando alias configurado for nulo")
    void testResolveAlias_NullConfiguredAlias_FallbackToFirstKeyEntry() throws Exception {
        CmsSigner signer = new CmsSigner();
        ReflectionTestUtils.setField(signer, "alias", null);
        ReflectionTestUtils.setField(signer, "password", "bry123456");

        KeyStore keyStore = cmsSigner.loadKeyStore();
        String resolved = signer.resolveAlias(keyStore);

        assertNotNull(resolved);
        assertTrue(keyStore.isKeyEntry(resolved));
    }

    @Test
    @DisplayName("Deve resolver o primeiro alias de chave quando alias configurado não for encontrado")
    void testResolveAlias_NotFoundConfiguredAlias_FallbackToFirstKeyEntry() throws Exception {
        CmsSigner signer = new CmsSigner();
        ReflectionTestUtils.setField(signer, "alias", "alias-inexistente");
        ReflectionTestUtils.setField(signer, "password", "bry123456");

        KeyStore keyStore = cmsSigner.loadKeyStore();
        String resolved = signer.resolveAlias(keyStore);

        assertNotNull(resolved);
        assertTrue(keyStore.isKeyEntry(resolved));
    }

    @Test
    @DisplayName("Deve lançar IllegalStateException quando não houver nenhuma entrada de chave no KeyStore")
    void testResolveAlias_NoKeyEntries_ThrowsException() throws Exception {
        KeyStore mockKs = mock(KeyStore.class);
        when(mockKs.containsAlias(anyString())).thenReturn(false);

        Vector<String> aliases = new Vector<>();
        aliases.add("cert-only-1");
        aliases.add("cert-only-2");
        when(mockKs.aliases()).thenReturn(aliases.elements());
        when(mockKs.isKeyEntry(anyString())).thenReturn(false);

        CmsSigner signer = new CmsSigner();
        ReflectionTestUtils.setField(signer, "alias", "alias-inexistente");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> signer.resolveAlias(mockKs));
        assertTrue(ex.getMessage().contains("Aliases disponíveis"));
    }

    @Test
    @DisplayName("Deve ignorar alias quando isKeyEntry lançar exceção e falhar se nenhum outro for válido")
    void testResolveAlias_KeyEntryCheckThrowsException() throws Exception {
        KeyStore mockKs = mock(KeyStore.class);
        when(mockKs.containsAlias(anyString())).thenReturn(false);

        Vector<String> aliases = new Vector<>();
        aliases.add("alias-com-erro");
        when(mockKs.aliases()).thenReturn(aliases.elements());
        when(mockKs.isKeyEntry("alias-com-erro")).thenThrow(new KeyStoreException("Erro simulado"));

        CmsSigner signer = new CmsSigner();
        ReflectionTestUtils.setField(signer, "alias", "alias-inexistente");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> signer.resolveAlias(mockKs));
        assertTrue(ex.getMessage().contains("Aliases disponíveis"));
    }
}
