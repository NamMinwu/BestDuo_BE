package com.bestduo_BE.common.infra.riot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LeagueEntry(
    @JsonProperty("puuid") String puuid,
    @JsonProperty("tier") String tier,
    @JsonProperty("queueType") String queueType,
    @JsonProperty("rank") String rank,
    @JsonProperty("leaguePoints") Long leaguePoints
) {}
