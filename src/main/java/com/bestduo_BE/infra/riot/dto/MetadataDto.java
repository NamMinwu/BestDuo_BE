package com.bestduo_BE.infra.riot.dto;

import java.util.List;

public record MetadataDto(
    String dataVersion,
    String matchId,
    List<String> participants
) {

}
