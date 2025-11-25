package com.bestduo_BE.infra.riot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InfoDto(
    Long gameCreation,
    Long gameDuration,
    Long gameEndTimestamp,
    Long gameId,
    String gameMode,
    String gameName,
    Long gameStartTimestamp,
    String gameType,
    String gameVersion,
    Integer mapId,
    String platformId,
    Integer queueId,
    List<ParticipantDto> participants,
    List<TeamDto> teams,
    String tournamentCode
) {}
