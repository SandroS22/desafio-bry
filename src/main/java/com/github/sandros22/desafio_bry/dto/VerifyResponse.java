package com.github.sandros22.desafio_bry.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VerifyResponse(
        @JsonProperty("Status") String Status,
        @JsonProperty("Infos") SignatureInfos Infos
) {
}
