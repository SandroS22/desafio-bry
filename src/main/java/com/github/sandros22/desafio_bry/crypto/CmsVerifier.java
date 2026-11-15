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

@Component
public class CmsVerifier {

    private final CmsSigner cmsSigner;

    public CmsVerifier(CmsSigner cmsSigner) {
        this.cmsSigner = cmsSigner;
    }

    public VerifyResponse verifyBase64Cms(byte[] base64FileBytes) throws Exception {
        byte[] cmsBytes = decodeBase64(base64FileBytes);
        CMSSignedData signedData = new CMSSignedData(cmsBytes);
        SignerInformation signer = signedData.getSignerInfos().getSigners().iterator().next();
        X509Certificate cert = extractSignerCert(signedData, signer);
        boolean isValid = verifyIntegrity(signer, cert) && verifyTrustChain(cert);
        return new VerifyResponse(isValid ? "VALIDO" : "INVALIDO", extractInfos(signedData, signer, cert));
    }

    private byte[] decodeBase64(byte[] raw) {
        String clean = new String(raw, StandardCharsets.UTF_8).replaceAll("\\s+", "");
        return Base64.getDecoder().decode(clean);
    }

    private SignatureInfos extractInfos(CMSSignedData data, SignerInformation signer, X509Certificate cert) throws Exception {
        String cn = extractCn(cert);
        String signingTime = extractSigningTime(signer);
        String digestAlg = new DefaultAlgorithmNameFinder().getAlgorithmName(signer.getDigestAlgorithmID());
        String docHash = computeContentHash(data, digestAlg);
        return new SignatureInfos(cn, signingTime, docHash, digestAlg);
    }

    private String extractCn(X509Certificate cert) throws Exception {
        X500Name x500Name = new X509CertificateHolder(cert.getEncoded()).getSubject();
        RDN[] rdns = x500Name.getRDNs(BCStyle.CN);
        return rdns.length > 0 ? IETFUtils.valueToString(rdns[0].getFirst().getValue()) : "";
    }

    private String extractSigningTime(SignerInformation signer) {
        if (signer.getSignedAttributes() == null) return null;
        Attribute attr = signer.getSignedAttributes().get(CMSAttributes.signingTime);
        if (attr == null) return null;
        return Time.getInstance(attr.getAttrValues().getObjectAt(0)).getDate().toInstant().toString();
    }

    private String computeContentHash(CMSSignedData data, String algorithm) throws Exception {
        byte[] content = (byte[]) data.getSignedContent().getContent();
        byte[] hash = MessageDigest.getInstance(algorithm).digest(content);
        return HexFormat.of().formatHex(hash);
    }

    private X509Certificate extractSignerCert(CMSSignedData data, SignerInformation signer) throws Exception {
        Collection<X509CertificateHolder> holders = data.getCertificates().getMatches(signer.getSID());
        if (holders.isEmpty()) throw new CMSException("Certificado do signatário ausente no CMS");
        return new JcaX509CertificateConverter().setProvider("BC").getCertificate(holders.iterator().next());
    }

    private boolean verifyIntegrity(SignerInformation signer, X509Certificate cert) {
        try {
            var verifier = new JcaSimpleSignerInfoVerifierBuilder().setProvider("BC").build(cert);
            return signer.verify(verifier);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean verifyTrustChain(X509Certificate signerCert) {
        try {
            List<X509Certificate> chain = loadChainCertificates();
            Set<TrustAnchor> anchors = getTrustAnchors(chain);
            if (anchors.stream().anyMatch(a -> a.getTrustedCert().equals(signerCert))) return true;
            CertPath path = CertificateFactory.getInstance("X.509").generateCertPath(List.of(signerCert));
            CertPathValidator.getInstance("PKIX").validate(path, buildPkixParams(anchors, chain));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private PKIXParameters buildPkixParams(Set<TrustAnchor> anchors, List<X509Certificate> intermediates) throws Exception {
        PKIXParameters params = new PKIXParameters(anchors);
        params.setRevocationEnabled(false);
        params.addCertStore(CertStore.getInstance("Collection", new CollectionCertStoreParameters(intermediates)));
        return params;
    }

    private Set<TrustAnchor> getTrustAnchors(List<X509Certificate> certs) {
        if (certs.isEmpty()) throw new IllegalStateException("Nenhum certificado encontrado no PKCS12");
        X509Certificate topCert = certs.stream()
                .filter(c -> c.getSubjectX500Principal().equals(c.getIssuerX500Principal()))
                .findFirst()
                .orElse(certs.get(certs.size() - 1));
        return Set.of(new TrustAnchor(topCert, null));
    }

    private List<X509Certificate> loadChainCertificates() throws Exception {
        KeyStore ks = cmsSigner.loadKeyStore();
        String keyAlias = cmsSigner.resolveAlias(ks);
        return Arrays.stream(ks.getCertificateChain(keyAlias))
                .map(c -> (X509Certificate) c)
                .toList();
    }
}