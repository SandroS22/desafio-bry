package com.github.sandros22.desafio_bry.dto;

public record SignatureInfos(
        String nomeSignatario,
        String dataAssinatura,
        String hashDocumento,
        String algoritmoHash
) {
}