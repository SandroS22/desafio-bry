package com.github.sandros22.desafio_bry.crypto;

import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.SignerInfoGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Component
public class CmsSigner {

    @Value("${bry.alias}")
    private String alias;

    @Value("${bry.password}")
    private String password;

    // Registra o provedor criptográfico BouncyCastle na JVM
    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    // Gera assinatura CMS attached com PKCS12 dinâmico ou padrão e grava assinatura.p7s em disco
    public byte[] signCmsAttached(byte[] content, byte[] pkcs12Bytes, String pfxPassword) throws Exception {
        KeyStore ks = (pkcs12Bytes != null && pkcs12Bytes.length > 0)
                ? loadKeyStore(pkcs12Bytes, pfxPassword)
                : loadKeyStore();
        String passToUse = (pfxPassword != null && !pfxPassword.isBlank()) ? pfxPassword : password;
        CMSSignedDataGenerator generator = createGenerator(ks, passToUse);
        // true anexa o conteúdo original dentro da estrutura CMS (attached)
        CMSSignedData signedData = generator.generate(new CMSProcessableByteArray(content), true);
        byte[] encoded = signedData.getEncoded();
        Files.write(Path.of("assinatura.p7s"), encoded);
        return encoded;
    }

    // Sobrecarga que assina usando o KeyStore e senha configurados por padrão
    public byte[] signCmsAttached(byte[] content) throws Exception {
        return signCmsAttached(content, null, null);
    }

    // Lê os bytes do documento padrão doc.txt do classpath ou diretório local
    public byte[] loadDefaultDoc() throws Exception {
        Resource resource = new ClassPathResource("arquivos/doc.txt");
        if (resource.exists()) {
            try (InputStream is = resource.getInputStream()) {
                return is.readAllBytes();
            }
        }
        Path fallback = Path.of("src/main/resources/arquivos/doc.txt");
        if (Files.exists(fallback)) {
            return Files.readAllBytes(fallback);
        }
        throw new IllegalStateException("Arquivo padrão doc.txt não encontrado");
    }

    // Carrega e assina o documento padrão doc.txt
    public byte[] signDefaultDoc() throws Exception {
        return signCmsAttached(loadDefaultDoc());
    }

    // Configura o gerador CMS com chave privada, certificado do signatário e cadeia completa
    private CMSSignedDataGenerator createGenerator(KeyStore ks, String keyPassword) throws Exception {
        String keyAlias = resolveAlias(ks);
        char[] passChars = (keyPassword != null) ? keyPassword.toCharArray() : new char[0];
        PrivateKey key = (PrivateKey) ks.getKey(keyAlias, passChars);
        X509Certificate cert = (X509Certificate) ks.getCertificate(keyAlias);
        CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
        gen.addSignerInfoGenerator(buildSignerInfo(key, cert));

        // Mescla certificados do KeyStore e da pasta cadeia/ sem duplicatas
        List<X509Certificate> certs = new ArrayList<>();
        var chain = ks.getCertificateChain(keyAlias);
        if (chain != null) {
            certs.addAll(Arrays.stream(chain).map(c -> (X509Certificate) c).toList());
        }
        for (X509Certificate cadeiaCert : loadCadeiaCertificates()) {
            if (!certs.contains(cadeiaCert)) {
                certs.add(cadeiaCert);
            }
        }
        gen.addCertificates(new JcaCertStore(certs));
        return gen;
    }

    // Carrega certificados X.509 da pasta resources/cadeia/ ignorando falhas individuais
    public List<X509Certificate> loadCadeiaCertificates() {
        List<X509Certificate> certs = new ArrayList<>();
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources("classpath*:cadeia/*");
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            for (Resource r : resources) {
                if (r.isReadable()) {
                    try (InputStream is = r.getInputStream()) {
                        certs.add((X509Certificate) cf.generateCertificate(is));
                    } catch (Exception ignored) {
                        // cai no fallback
                    }
                }
            }
        } catch (Exception ignored) {
            // cai no fallback
        }
        return certs;
    }

    // Busca o alias configurado no KeyStore ou seleciona a primeira chave privada disponível
    public String resolveAlias(KeyStore ks) throws Exception {
        if (alias != null && ks.containsAlias(alias.trim())) return alias.trim();
        List<String> aliases = Collections.list(ks.aliases());
        return aliases.stream().filter(a -> {
            try {
                return ks.isKeyEntry(a);
            } catch (Exception e) {
                return false;
            }
        }).findFirst().orElseThrow(() -> new IllegalStateException("Aliases disponíveis: " + aliases));
    }

    // Constrói as informações do signatário utilizando SHA-512 com RSA
    private SignerInfoGenerator buildSignerInfo(PrivateKey key, X509Certificate cert) throws Exception {
        ContentSigner signer = new JcaContentSignerBuilder("SHA512withRSA").setProvider("BC").build(key);
        DigestCalculatorProvider digestProvider = new JcaDigestCalculatorProviderBuilder().setProvider("BC").build();
        return new JcaSignerInfoGeneratorBuilder(digestProvider).build(signer, cert);
    }

    // Carrega um KeyStore PKCS12 em memória a partir de bytes e senha informados
    public KeyStore loadKeyStore(byte[] pkcs12Bytes, String pfxPassword) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        char[] passChars = (pfxPassword != null) ? pfxPassword.toCharArray() : new char[0];
        try (InputStream is = new ByteArrayInputStream(pkcs12Bytes)) {
            keyStore.load(is, passChars);
        }
        return keyStore;
    }

    // Carrega o arquivo PKCS12 padrão localizado em resources/pkcs12/
    public KeyStore loadKeyStore() throws Exception {
        Resource[] resources = new PathMatchingResourcePatternResolver().getResources("classpath*:pkcs12/*");
        if (resources.length == 0)
            throw new IllegalStateException("Arquivo PKCS12 não encontrado em resources/pkcs12/");
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream is = resources[0].getInputStream()) {
            keyStore.load(is, password != null ? password.toCharArray() : new char[0]);
        }
        return keyStore;
    }
}