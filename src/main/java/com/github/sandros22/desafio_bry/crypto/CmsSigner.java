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
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

@Component
public class CmsSigner {

    @Value("${bry.alias}")
    private String alias;

    @Value("${bry.password}")
    private String password;

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public byte[] signCmsAttached(byte[] content) throws Exception {
        CMSSignedDataGenerator generator = createGenerator(loadKeyStore());
        CMSSignedData signedData = generator.generate(new CMSProcessableByteArray(content), true);
        byte[] encoded = signedData.getEncoded();
        Files.writeString(Path.of("assinatura.txt"), Base64.getEncoder().encodeToString(encoded));
        return encoded;
    }

    private CMSSignedDataGenerator createGenerator(KeyStore ks) throws Exception {
        String keyAlias = resolveAlias(ks);
        PrivateKey key = (PrivateKey) ks.getKey(keyAlias, password.toCharArray());
        X509Certificate cert = (X509Certificate) ks.getCertificate(keyAlias);
        CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
        gen.addSignerInfoGenerator(buildSignerInfo(key, cert));
        gen.addCertificates(new JcaCertStore(List.of(ks.getCertificateChain(keyAlias))));
        return gen;
    }

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

    private SignerInfoGenerator buildSignerInfo(PrivateKey key, X509Certificate cert) throws Exception {
        ContentSigner signer = new JcaContentSignerBuilder("SHA512withRSA").setProvider("BC").build(key);
        DigestCalculatorProvider digestProvider = new JcaDigestCalculatorProviderBuilder().setProvider("BC").build();
        return new JcaSignerInfoGeneratorBuilder(digestProvider).build(signer, cert);
    }

    public KeyStore loadKeyStore() throws Exception {
        Resource[] resources = new PathMatchingResourcePatternResolver().getResources("classpath:pkcs12/*");
        if (resources.length == 0)
            throw new IllegalStateException("Arquivo PKCS12 não encontrado em resources/pkcs12/");
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream is = resources[0].getInputStream()) {
            keyStore.load(is, password.toCharArray());
        }
        return keyStore;
    }
}