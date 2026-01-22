package com.bestduo_BE.infra.riot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LeagueEntry(
    @JsonProperty("puuid") String puuid
) {}
