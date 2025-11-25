package com.bestduo_BE.infra.riot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TeamDto(
    Integer teamId,
    Boolean win
) {}
