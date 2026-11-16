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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

@RestController
public class Controller {

    private final CmsSigner signer;
    private final CmsVerifier verifier;

    // Injeta os componentes responsáveis pela assinatura e verificação CMS
    public Controller(CmsSigner signer, CmsVerifier verifier) {
        this.signer = signer;
        this.verifier = verifier;
    }

    // Recebe arquivo, certificado e senha via multipart, assina e retorna o CMS em Base64
    @PostMapping(value = {"/signature/"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> signFile(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "pkcs12", required = false) MultipartFile pkcs12,
            @RequestParam(value = "password", required = false) String password
    ) throws Exception {
        // Utiliza o doc.txt padrão caso nenhum arquivo seja enviado na requisição
        byte[] content = (file == null || file.isEmpty()) ? signer.loadDefaultDoc() : file.getBytes();
        byte[] pkcs12Bytes = (pkcs12 != null && !pkcs12.isEmpty()) ? pkcs12.getBytes() : null;

        byte[] signedBytes = signer.signCmsAttached(content, pkcs12Bytes, password);
        return ResponseEntity.ok(Base64.getEncoder().encodeToString(signedBytes));
    }

    // Valida assinatura CMS enviada ou usa assinatura.p7s em disco como fallback, retornando status e metadados
    @PostMapping(value = {"/verify/"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VerifyResponse> verifySignature(
            @RequestParam(value = "file", required = false) MultipartFile file
    ) throws Exception {
        byte[] bytes;
        if (file == null || file.isEmpty()) {
            Path p7sPath = Path.of("assinatura.p7s");
            if (Files.exists(p7sPath)) {
                bytes = Files.readAllBytes(p7sPath);
            } else {
                // Dispara erro 400 caso não haja arquivo na requisição nem em disco
                throw new IllegalArgumentException("O arquivo para verificação não pode estar vazio");
            }
        } else {
            bytes = file.getBytes();
        }
        return ResponseEntity.ok(verifier.verifyBase64Cms(bytes));
    }
}