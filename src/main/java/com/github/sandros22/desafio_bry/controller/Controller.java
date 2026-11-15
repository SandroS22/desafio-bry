package com.github.sandros22.desafio_bry.controller;

import com.github.sandros22.desafio_bry.crypto.CmsVerifier;
import com.github.sandros22.desafio_bry.crypto.CmsSigner;
import com.github.sandros22.desafio_bry.dto.VerifyResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;

@RestController
public class Controller {

    private final CmsSigner signer;
    private final CmsVerifier verifier;

    public Controller(CmsSigner signer, CmsVerifier verifier) {
        this.signer = signer;
        this.verifier = verifier;
    }

    @PostMapping(value = "/sign", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> signFile(@RequestParam("file") MultipartFile file) throws Exception {
        if (file.isEmpty()) throw new IllegalArgumentException("O arquivo para assinatura não pode estar vazio");
        byte[] signedBytes = signer.signCmsAttached(file.getBytes());
        return ResponseEntity.ok(Base64.getEncoder().encodeToString(signedBytes));
    }

    @PostMapping(value = "/verify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VerifyResponse> verifySignature(@RequestParam("file") MultipartFile file) throws Exception {
        return ResponseEntity.ok(verifier.verifyBase64Cms(file.getBytes()));
    }
}