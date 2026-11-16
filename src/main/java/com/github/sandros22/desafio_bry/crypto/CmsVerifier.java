package com.github.sandros22.desafio_bry.crypto;

import com.github.sandros22.desafio_bry.dto.SignatureInfos;
import com.github.sandros22.desafio_bry.dto.VerifyResponse;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.CMSAttributes;
import org.bouncycastle.asn1.cms.Time;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.operator.DefaultAlgorithmNameFinder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.*;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class CmsVerifier {

    private final CmsSigner cmsSigner;

    public CmsVerifier(CmsSigner cmsSigner) {
        this.cmsSigner = cmsSigner;
    }

    // Valida integridade, cadeia de confiança e extrai os metadados da assinatura CMS
    public VerifyResponse verifyBase64Cms(byte[] base64FileBytes) throws Exception {
        byte[] cmsBytes = decodeBase64(base64FileBytes);
        CMSSignedData signedData = new CMSSignedData(cmsBytes);
        SignerInformation signer = signedData.getSignerInfos().getSigners().iterator().next();
        X509Certificate cert = extractSignerCert(signedData, signer);
        boolean isValid = verifyIntegrity(signer, cert) && verifyTrustChain(cert);
        return new VerifyResponse(isValid ? "VALIDO" : "INVALIDO", extractInfos(signedData, signer, cert));
    }

    // Aceita DER binário (tag ASN.1 0x30), PEM ou Base64 puro e retorna bytes CMS
    private byte[] decodeBase64(byte[] raw) {
        if (raw.length > 0 && raw[0] == 0x30) {
            try {
                new CMSSignedData(raw);
                return raw;
            } catch (Exception ignored) {
            }
        }
        String text = new String(raw, StandardCharsets.UTF_8).trim();
        if (text.contains("-----BEGIN")) {
            text = text.replaceAll("-----BEGIN[^-]+-----", "")
                    .replaceAll("-----END[^-]+-----", "");
        }
        String clean = text.replaceAll("\\s+", "");
        return Base64.getDecoder().decode(clean);
    }

    // Agrega CN do signatário, data da assinatura, hash e algoritmo de resumo
    private SignatureInfos extractInfos(CMSSignedData data, SignerInformation signer, X509Certificate cert) throws Exception {
        String cn = extractCn(cert);
        String signingTime = extractSigningTime(signer);
        String digestAlg = new DefaultAlgorithmNameFinder().getAlgorithmName(signer.getDigestAlgorithmID());
        String docHash = computeContentHash(data, digestAlg);
        return new SignatureInfos(cn, signingTime, docHash, digestAlg);
    }

    // Extrai o atributo Common Name (CN) do assunto do certificado X.509
    private String extractCn(X509Certificate cert) throws Exception {
        X500Name x500Name = new X509CertificateHolder(cert.getEncoded()).getSubject();
        RDN[] rdns = x500Name.getRDNs(BCStyle.CN);
        return rdns.length > 0 ? IETFUtils.valueToString(rdns[0].getFirst().getValue()) : "";
    }

    // Obtém o atributo signingTime das propriedades assinadas
    private String extractSigningTime(SignerInformation signer) {
        if (signer.getSignedAttributes() == null) return null;
        Attribute attr = signer.getSignedAttributes().get(CMSAttributes.signingTime);
        if (attr == null) return null;
        return Time.getInstance(attr.getAttrValues().getObjectAt(0)).getDate().toInstant().toString();
    }

    // Calcula o resumo criptográfico do conteúdo anexado em formato hexadecimal
    private String computeContentHash(CMSSignedData data, String algorithm) throws Exception {
        byte[] content = (byte[]) data.getSignedContent().getContent();
        byte[] hash = MessageDigest.getInstance(algorithm).digest(content);
        return HexFormat.of().formatHex(hash);
    }

    // Localiza e converte o certificado correspondente ao identificador do signatário no CMS
    private X509Certificate extractSignerCert(CMSSignedData data, SignerInformation signer) throws Exception {
        Collection<X509CertificateHolder> holders = data.getCertificates().getMatches(signer.getSID());
        if (holders.isEmpty()) throw new CMSException("Certificado do signatário ausente no CMS");
        return new JcaX509CertificateConverter().setProvider("BC").getCertificate(holders.iterator().next());
    }

    // Verifica a assinatura digital contra a chave pública do signatário
    private boolean verifyIntegrity(SignerInformation signer, X509Certificate cert) {
        try {
            var verifier = new JcaSimpleSignerInfoVerifierBuilder().setProvider("BC").build(cert);
            return signer.verify(verifier);
        } catch (Exception e) {
            return false;
        }
    }

    // Constrói e valida o caminho de certificação PKIX até uma âncora confiável
    private boolean verifyTrustChain(X509Certificate signerCert) {
        try {
            List<X509Certificate> chain = loadChainCertificates();
            Set<TrustAnchor> anchors = getTrustAnchors(chain);
            if (anchors.stream().anyMatch(a -> a.getTrustedCert().equals(signerCert))) return true;

            List<X509Certificate> allCerts = new ArrayList<>(chain);
            if (!allCerts.contains(signerCert)) {
                allCerts.add(signerCert);
            }

            try {
                X509CertSelector selector = new X509CertSelector();
                selector.setCertificate(signerCert);
                PKIXBuilderParameters builderParams = new PKIXBuilderParameters(anchors, selector);
                builderParams.setRevocationEnabled(false);
                builderParams.addCertStore(CertStore.getInstance("Collection", new CollectionCertStoreParameters(allCerts)));
                CertPathBuilder.getInstance("PKIX").build(builderParams);
                return true;
            } catch (Exception e) {
                // Fallback de validação direta caso a construção automática do caminho falhe
                CertPath path = CertificateFactory.getInstance("X.509").generateCertPath(List.of(signerCert));
                CertPathValidator.getInstance("PKIX").validate(path, buildPkixParams(anchors, chain));
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

    // Configura parâmetros PKIX e anexa certificados intermediários
    private PKIXParameters buildPkixParams(Set<TrustAnchor> anchors, List<X509Certificate> intermediates) throws Exception {
        PKIXParameters params = new PKIXParameters(anchors);
        params.setRevocationEnabled(false);
        params.addCertStore(CertStore.getInstance("Collection", new CollectionCertStoreParameters(intermediates)));
        return params;
    }

    // Identifica certificados raiz autoassinados como âncoras de confiança ou usa o topo da cadeia
    private Set<TrustAnchor> getTrustAnchors(List<X509Certificate> certs) {
        if (certs.isEmpty()) throw new IllegalStateException("Nenhum certificado encontrado para cadeia de confiança");
        List<X509Certificate> roots = certs.stream()
                .filter(c -> c.getSubjectX500Principal().equals(c.getIssuerX500Principal()))
                .toList();
        if (!roots.isEmpty()) {
            return roots.stream().map(r -> new TrustAnchor(r, null)).collect(Collectors.toSet());
        }
        return Set.of(new TrustAnchor(certs.get(certs.size() - 1), null));
    }

    // Carrega cadeia de resources/cadeia/ com fallback para a cadeia do KeyStore
    public List<X509Certificate> loadChainCertificates() throws Exception {
        List<X509Certificate> chain = new ArrayList<>(cmsSigner.loadCadeiaCertificates());
        if (chain.isEmpty()) {
            KeyStore ks = cmsSigner.loadKeyStore();
            String keyAlias = cmsSigner.resolveAlias(ks);
            var ksCerts = ks.getCertificateChain(keyAlias);
            if (ksCerts != null) {
                for (var c : ksCerts) {
                    chain.add((X509Certificate) c);
                }
            }
        }
        return chain;
    }
}