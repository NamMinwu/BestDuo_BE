package com.bestduo_BE.infra.riot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RiotMatchDto(
    @JsonProperty("metadata") MetadataDto metadata,
    @JsonProperty("info") InfoDto info
) {

}
