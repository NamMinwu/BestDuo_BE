package com.bestduo_BE.infra.riot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ParticipantDto(
    String puuid,
    String riotIdGameName,
    String riotIdTagline,
    String summonerName,
    Integer profileIcon,
    Integer summonerLevel,
    Integer championId,
    String championName,
    Integer championTransform,
    Integer champExperience,
    Integer kills,
    Integer deaths,
    Integer assists,
    Integer totalMinionsKilled,
    Integer neutralMinionsKilled,
    Integer totalDamageDealtToChampions,
    Integer totalDamageTaken,
    Integer goldEarned,
    Integer teamId,
    String individualPosition,
    String lane,
    String role,
    Boolean win
) {}

