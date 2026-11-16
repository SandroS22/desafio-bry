package com.github.sandros22.desafio_bry.crypto;

import com.github.sandros22.desafio_bry.dto.VerifyResponse;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.*;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class CmsVerifierTest {

    @Autowired
    private CmsSigner cmsSigner;

    @Autowired
    private CmsVerifier cmsVerifier;

    @Test
    @DisplayName("Deve verificar diretamente arquivo binário DER (.p7s) sem codificação Base64")
    void testVerifyDirectBinaryP7s() throws Exception {
        byte[] content = "Conteudo para teste direto de .p7s".getBytes(StandardCharsets.UTF_8);
        byte[] signedBytes = cmsSigner.signCmsAttached(content);

        // Passa diretamente os bytes binários (sem converter para base64)
        VerifyResponse response = cmsVerifier.verifyBase64Cms(signedBytes);

        assertNotNull(response);
        assertEquals("VALIDO", response.Status());
        assertNotNull(response.Infos());
    }

    @Test
    @DisplayName("Deve verificar assinatura formatada em PEM (com delimitadores -----BEGIN CMS-----)")
    void testVerifyPemFormattedCms() throws Exception {
        byte[] content = "Conteudo para teste PEM".getBytes(StandardCharsets.UTF_8);
        byte[] signedBytes = cmsSigner.signCmsAttached(content);
        String base64 = Base64.getEncoder().encodeToString(signedBytes);
        String pem = "-----BEGIN CMS-----\n" + base64 + "\n-----END CMS-----\n";

        VerifyResponse response = cmsVerifier.verifyBase64Cms(pem.getBytes(StandardCharsets.UTF_8));
        assertNotNull(response);
        assertEquals("VALIDO", response.Status());
        assertNotNull(response.Infos());
    }

    @Test
    @DisplayName("Deve fazer fallback para a cadeia do KeyStore quando a pasta cadeia estiver vazia")
    void testLoadChainCertificates_FallbackToKeyStoreWhenCadeiaEmpty() throws Exception {
        CmsSigner mockSigner = org.mockito.Mockito.mock(CmsSigner.class);
        KeyStore mockKs = org.mockito.Mockito.mock(KeyStore.class);

        // Retorna cadeia vazia para recursos/cadeia/
        org.mockito.Mockito.when(mockSigner.loadCadeiaCertificates()).thenReturn(Collections.emptyList());
        org.mockito.Mockito.when(mockSigner.loadKeyStore()).thenReturn(mockKs);
        org.mockito.Mockito.when(mockSigner.resolveAlias(mockKs)).thenReturn("mock-alias");

        X509Certificate mockCert = org.mockito.Mockito.mock(X509Certificate.class);
        org.mockito.Mockito.when(mockKs.getCertificateChain("mock-alias")).thenReturn(new java.security.cert.Certificate[]{mockCert});

        CmsVerifier verifier = new CmsVerifier(mockSigner);
        List<X509Certificate> chain = verifier.loadChainCertificates();

        assertNotNull(chain);
        assertEquals(1, chain.size());
        assertEquals(mockCert, chain.get(0));
    }

    @Test
    @DisplayName("Deve verificar com sucesso uma assinatura válida gerada pelo CmsSigner")
    void testVerifyBase64Cms_ValidSignature() throws Exception {
        byte[] content = "Conteudo de teste para validacao de assinatura".getBytes(StandardCharsets.UTF_8);
        byte[] signedBytes = cmsSigner.signCmsAttached(content);
        byte[] base64Bytes = Base64.getEncoder().encode(signedBytes);

        VerifyResponse response = cmsVerifier.verifyBase64Cms(base64Bytes);

        assertNotNull(response);
        assertEquals("VALIDO", response.Status());
        assertNotNull(response.Infos());
        assertNotNull(response.Infos().nomeSignatario());
        assertFalse(response.Infos().nomeSignatario().isBlank());
        assertNotNull(response.Infos().dataAssinatura());
        assertNotNull(response.Infos().hashDocumento());
        assertEquals("SHA512", response.Infos().algoritmoHash());
    }

    @Test
    @DisplayName("Deve decodificar e verificar assinatura em Base64 contendo quebras de linha e espaços")
    void testVerifyBase64Cms_WithWhitespaceAndNewlines() throws Exception {
        byte[] content = "Teste com espacos e quebras de linha".getBytes(StandardCharsets.UTF_8);
        byte[] signedBytes = cmsSigner.signCmsAttached(content);
        String base64Str = Base64.getEncoder().encodeToString(signedBytes);
        String base64WithFormatting = "\n  " + base64Str.substring(0, 50) + "\r\n  " + base64Str.substring(50) + " \n";

        VerifyResponse response = cmsVerifier.verifyBase64Cms(base64WithFormatting.getBytes(StandardCharsets.UTF_8));

        assertNotNull(response);
        assertEquals("VALIDO", response.Status());
        assertNotNull(response.Infos());
    }

    @Test
    @DisplayName("Deve retornar INVALIDO quando o conteúdo assinado for adulterado (falha de integridade)")
    void testVerifyBase64Cms_TamperedContent_ReturnsInvalido() throws Exception {
        String targetText = "CONTEUDO_ORIGINAL_PARA_TESTE_INTEGRIDADE";
        byte[] content = targetText.getBytes(StandardCharsets.UTF_8);
        byte[] signedBytes = cmsSigner.signCmsAttached(content);

        // Clona e altera um caractere do conteúdo dentro do CMS mantendo a estrutura ASN.1 intacta
        byte[] tamperedBytes = signedBytes.clone();
        byte[] targetPattern = "ORIGINAL".getBytes(StandardCharsets.UTF_8);
        int index = -1;
        for (int i = 0; i <= tamperedBytes.length - targetPattern.length; i++) {
            boolean match = true;
            for (int j = 0; j < targetPattern.length; j++) {
                if (tamperedBytes[i + j] != targetPattern[j]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                index = i;
                break;
            }
        }
        assertTrue(index != -1, "Padrão de conteúdo não encontrado no CMS");
        tamperedBytes[index] = (byte) 'X'; // Altera 'O' para 'X'

        byte[] tamperedBase64 = Base64.getEncoder().encode(tamperedBytes);

        VerifyResponse response = cmsVerifier.verifyBase64Cms(tamperedBase64);

        assertNotNull(response);
        assertEquals("INVALIDO", response.Status());
        assertNotNull(response.Infos());
    }

    @Test
    @DisplayName("Deve lançar CMSException quando o certificado do signatário estiver ausente no CMS")
    void testVerifyBase64Cms_MissingSignerCert_ThrowsCMSException() throws Exception {
        byte[] content = "Conteudo teste".getBytes(StandardCharsets.UTF_8);
        byte[] signedBytes = cmsSigner.signCmsAttached(content);
        CMSSignedData signedData = new CMSSignedData(signedBytes);

        // Cria CMSSignedData sem nenhum certificado anexado
        CMSSignedData dataWithoutCerts = CMSSignedData.replaceCertificatesAndCRLs(
                signedData,
                new JcaCertStore(Collections.emptyList()),
                null,
                null
        );

        byte[] base64Bytes = Base64.getEncoder().encode(dataWithoutCerts.getEncoded());

        CMSException exception = assertThrows(CMSException.class, () -> cmsVerifier.verifyBase64Cms(base64Bytes));
        assertEquals("Certificado do signatário ausente no CMS", exception.getMessage());
    }

    @Test
    @DisplayName("Deve retornar INVALIDO quando o certificado não pertencer à cadeia de confiança do KeyStore")
    void testVerifyBase64Cms_UntrustedCertificateChain_ReturnsInvalido() throws Exception {
        // Gera par de chaves e certificado autoassinado não confiável
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair pair = kpg.generateKeyPair();

        long now = System.currentTimeMillis();
        X500Name name = new X500Name("CN=Untrusted Signer, O=Untrusted, C=BR");
        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                name,
                BigInteger.valueOf(now),
                new Date(now - 10000),
                new Date(now + 1000000),
                name,
                pair.getPublic()
        );
        ContentSigner certSigner = new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(pair.getPrivate());
        X509Certificate untrustedCert = new JcaX509CertificateConverter().setProvider("BC").getCertificate(certBuilder.build(certSigner));

        // Cria CMS assinado com este certificado não confiável
        CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
        ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(pair.getPrivate());
        DigestCalculatorProvider digestCalcProvider = new JcaDigestCalculatorProviderBuilder().setProvider("BC").build();
        gen.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(digestCalcProvider).build(contentSigner, untrustedCert));
        gen.addCertificates(new JcaCertStore(List.of(untrustedCert)));

        byte[] content = "Conteudo assinado com cert nao confiavel".getBytes(StandardCharsets.UTF_8);
        CMSSignedData signedData = gen.generate(new CMSProcessableByteArray(content), true);
        byte[] base64Bytes = Base64.getEncoder().encode(signedData.getEncoded());

        VerifyResponse response = cmsVerifier.verifyBase64Cms(base64Bytes);

        assertNotNull(response);
        assertEquals("INVALIDO", response.Status());
        assertNotNull(response.Infos());
        assertEquals("Untrusted Signer", response.Infos().nomeSignatario());
    }

    @Test
    @DisplayName("Deve extrair signingTime como nulo quando não houver o atributo no CMS")
    void testVerifyBase64Cms_WithoutSigningTimeAttribute() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair pair = kpg.generateKeyPair();

        long now = System.currentTimeMillis();
        X500Name name = new X500Name("CN=Signer Sem SigningTime");
        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                name,
                BigInteger.valueOf(now),
                new Date(now - 10000),
                new Date(now + 1000000),
                name,
                pair.getPublic()
        );
        ContentSigner certSigner = new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(pair.getPrivate());
        X509Certificate cert = new JcaX509CertificateConverter().setProvider("BC").getCertificate(certBuilder.build(certSigner));

        // Gerador com signedAttributeGenerator customizado sem signingTime
        CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
        ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(pair.getPrivate());
        DigestCalculatorProvider digestCalcProvider = new JcaDigestCalculatorProviderBuilder().setProvider("BC").build();

        // JcaSignerInfoGeneratorBuilder com directSignature=true não inclui atributos assinados padrão (signingTime)
        JcaSignerInfoGeneratorBuilder infoGenBuilder = new JcaSignerInfoGeneratorBuilder(digestCalcProvider);
        infoGenBuilder.setDirectSignature(true);

        gen.addSignerInfoGenerator(infoGenBuilder.build(contentSigner, cert));
        gen.addCertificates(new JcaCertStore(List.of(cert)));

        byte[] content = "Conteudo sem data".getBytes(StandardCharsets.UTF_8);
        CMSSignedData signedData = gen.generate(new CMSProcessableByteArray(content), true);
        byte[] base64Bytes = Base64.getEncoder().encode(signedData.getEncoded());

        VerifyResponse response = cmsVerifier.verifyBase64Cms(base64Bytes);

        assertNotNull(response);
        assertNull(response.Infos().dataAssinatura());
        assertEquals("Signer Sem SigningTime", response.Infos().nomeSignatario());
    }

    @Test
    @DisplayName("Deve extrair nomeSignatario como string vazia quando o certificado não tiver CN")
    void testVerifyBase64Cms_CertificateWithoutCn() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair pair = kpg.generateKeyPair();

        long now = System.currentTimeMillis();
        // X500Name sem CN, apenas O (Organization)
        X500Name name = new X500Name("O=Organizacao Sem CN, C=BR");
        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                name,
                BigInteger.valueOf(now),
                new Date(now - 10000),
                new Date(now + 1000000),
                name,
                pair.getPublic()
        );
        ContentSigner certSigner = new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(pair.getPrivate());
        X509Certificate cert = new JcaX509CertificateConverter().setProvider("BC").getCertificate(certBuilder.build(certSigner));

        CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
        ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(pair.getPrivate());
        DigestCalculatorProvider digestCalcProvider = new JcaDigestCalculatorProviderBuilder().setProvider("BC").build();
        gen.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(digestCalcProvider).build(contentSigner, cert));
        gen.addCertificates(new JcaCertStore(List.of(cert)));

        byte[] content = "Conteudo sem CN".getBytes(StandardCharsets.UTF_8);
        CMSSignedData signedData = gen.generate(new CMSProcessableByteArray(content), true);
        byte[] base64Bytes = Base64.getEncoder().encode(signedData.getEncoded());

        VerifyResponse response = cmsVerifier.verifyBase64Cms(base64Bytes);

        assertNotNull(response);
        assertEquals("", response.Infos().nomeSignatario());
    }

    @Test
    @DisplayName("Deve lançar IllegalArgumentException para entrada com Base64 inválido")
    void testVerifyBase64Cms_InvalidBase64_ThrowsIllegalArgumentException() {
        byte[] invalidBase64 = "### Não é base64 válido ###".getBytes(StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> cmsVerifier.verifyBase64Cms(invalidBase64));
    }

    @Test
    @DisplayName("Deve lançar CMSException quando bytes decodificados não forem um objeto CMS válido")
    void testVerifyBase64Cms_InvalidCmsStructure_ThrowsCMSException() {
        byte[] randomBytes = "conteudo plano qualquer".getBytes(StandardCharsets.UTF_8);
        byte[] base64Bytes = Base64.getEncoder().encode(randomBytes);
        assertThrows(CMSException.class, () -> cmsVerifier.verifyBase64Cms(base64Bytes));
    }

    @Test
    @DisplayName("Deve validar com sucesso caminho de certificação PKIX quando o signatário for folha emitido pela autoridade raiz")
    void testVerifyBase64Cms_PkixCertPathValidationSuccess() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair caPair = kpg.generateKeyPair();
        KeyPair eePair = kpg.generateKeyPair();

        long now = System.currentTimeMillis();
        // CA cert
        X500Name caName = new X500Name("CN=Root CA, O=Test Org, C=BR");
        X509v3CertificateBuilder caCertBuilder = new JcaX509v3CertificateBuilder(
                caName,
                BigInteger.valueOf(1),
                new Date(now - 10000),
                new Date(now + 10000000),
                caName,
                caPair.getPublic()
        );
        caCertBuilder.addExtension(org.bouncycastle.asn1.x509.Extension.basicConstraints, true, new org.bouncycastle.asn1.x509.BasicConstraints(true));
        caCertBuilder.addExtension(org.bouncycastle.asn1.x509.Extension.keyUsage, true, new org.bouncycastle.asn1.x509.KeyUsage(org.bouncycastle.asn1.x509.KeyUsage.keyCertSign | org.bouncycastle.asn1.x509.KeyUsage.cRLSign));
        ContentSigner caSigner = new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(caPair.getPrivate());
        X509Certificate caCert = new JcaX509CertificateConverter().setProvider("BC").getCertificate(caCertBuilder.build(caSigner));

        // EE (End Entity) cert
        X500Name eeName = new X500Name("CN=Leaf Signer, O=Test Org, C=BR");
        X509v3CertificateBuilder eeCertBuilder = new JcaX509v3CertificateBuilder(
                caName,
                BigInteger.valueOf(2),
                new Date(now - 10000),
                new Date(now + 10000000),
                eeName,
                eePair.getPublic()
        );
        eeCertBuilder.addExtension(org.bouncycastle.asn1.x509.Extension.basicConstraints, true, new org.bouncycastle.asn1.x509.BasicConstraints(false));
        eeCertBuilder.addExtension(org.bouncycastle.asn1.x509.Extension.keyUsage, true, new org.bouncycastle.asn1.x509.KeyUsage(org.bouncycastle.asn1.x509.KeyUsage.digitalSignature));
        X509Certificate eeCert = new JcaX509CertificateConverter().setProvider("BC").getCertificate(eeCertBuilder.build(caSigner));

        // Assina dados com eePair e eeCert
        CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
        ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(eePair.getPrivate());
        DigestCalculatorProvider digestCalcProvider = new JcaDigestCalculatorProviderBuilder().setProvider("BC").build();
        gen.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(digestCalcProvider).build(contentSigner, eeCert));
        gen.addCertificates(new JcaCertStore(List.of(eeCert, caCert)));

        byte[] content = "Conteudo com cadeia PKIX".getBytes(StandardCharsets.UTF_8);
        CMSSignedData signedData = gen.generate(new CMSProcessableByteArray(content), true);
        byte[] base64Bytes = Base64.getEncoder().encode(signedData.getEncoded());

        // Mock CmsSigner para retornar a cadeia [eeCert, caCert]
        CmsSigner mockSigner = org.mockito.Mockito.mock(CmsSigner.class);
        KeyStore mockKs = org.mockito.Mockito.mock(KeyStore.class);
        org.mockito.Mockito.when(mockSigner.loadKeyStore()).thenReturn(mockKs);
        org.mockito.Mockito.when(mockSigner.resolveAlias(mockKs)).thenReturn("leaf-alias");
        org.mockito.Mockito.when(mockKs.getCertificateChain("leaf-alias")).thenReturn(new java.security.cert.Certificate[]{eeCert, caCert});

        CmsVerifier verifierWithMockSigner = new CmsVerifier(mockSigner);
        VerifyResponse response = verifierWithMockSigner.verifyBase64Cms(base64Bytes);

        assertNotNull(response);
        assertEquals("VALIDO", response.Status());
        assertEquals("Leaf Signer", response.Infos().nomeSignatario());
    }

    @Test
    @DisplayName("Deve retornar INVALIDO quando a cadeia do KeyStore estiver vazia (getTrustAnchors)")
    void testVerifyBase64Cms_EmptyChain_ReturnsInvalido() throws Exception {
        byte[] content = "Conteudo qualquer".getBytes(StandardCharsets.UTF_8);
        byte[] signedBytes = cmsSigner.signCmsAttached(content);
        byte[] base64Bytes = Base64.getEncoder().encode(signedBytes);

        CmsSigner mockSigner = org.mockito.Mockito.mock(CmsSigner.class);
        KeyStore mockKs = org.mockito.Mockito.mock(KeyStore.class);
        org.mockito.Mockito.when(mockSigner.loadKeyStore()).thenReturn(mockKs);
        org.mockito.Mockito.when(mockSigner.resolveAlias(mockKs)).thenReturn("alias");
        org.mockito.Mockito.when(mockKs.getCertificateChain("alias")).thenReturn(new java.security.cert.Certificate[0]);

        CmsVerifier verifierWithMock = new CmsVerifier(mockSigner);
        VerifyResponse response = verifierWithMock.verifyBase64Cms(base64Bytes);

        assertNotNull(response);
        assertEquals("INVALIDO", response.Status());
    }
}
