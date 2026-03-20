package com.bestduo_BE.support;

import com.bestduo_BE.common.infra.riot.dto.InfoDto;
import com.bestduo_BE.common.infra.riot.dto.ParticipantDto;
import com.bestduo_BE.common.infra.riot.dto.RiotMatchDto;
import com.bestduo_BE.common.infra.riot.dto.TeamDto;
import java.util.List;

public final class RiotMatchTestData {

  private RiotMatchTestData() {}

  public static RiotMatchDto riotMatch(String version, List<ParticipantDto> participants, List<TeamDto> teams) {
    return new RiotMatchDto(
        null,
        new InfoDto(
            null, null, null, null, null, null, null, null,
            version,
            null, null, null,
            participants,
            teams,
            null
        )
    );
  }

  public static ParticipantDto participant(int teamId, String position, int championId) {
    return new ParticipantDto(
        null, null, null, null,
        null, null,
        championId,
        null,
        null, null,
        null, null, null,
        null, null,
        null, null,
        null,
        teamId,
        position,
        null,
        null,
        null
    );
  }

  public static TeamDto team(int teamId, boolean win) {
    return new TeamDto(teamId, win);
  }
}
