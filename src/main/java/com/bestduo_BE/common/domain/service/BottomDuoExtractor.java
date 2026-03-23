package com.bestduo_BE.common.domain.service;

import com.bestduo_BE.common.domain.model.BottomDuoRaw;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.riot.dto.InfoDto;
import com.bestduo_BE.common.infra.riot.dto.ParticipantDto;
import com.bestduo_BE.common.infra.riot.dto.RiotMatchDto;
import com.bestduo_BE.common.infra.riot.dto.TeamDto;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BottomDuoExtractor {

  public List<BottomDuoRaw> extract(String matchId, RiotMatchDto match, Tier tier) {
    if (match == null || match.info() == null) {
      return List.of();
    }

    InfoDto info = match.info();
    if (info.participants() == null) {
      return List.of();
    }

    String patch = toPatch(info.gameVersion());
    Map<Integer, Boolean> teamWinMap = buildTeamWinMap(info.teams());
    TeamParticipants teamParticipants = extractTeamParticipants(info.participants());
    return createBottomDuoRows(matchId, tier, patch, teamWinMap, teamParticipants);
  }

  private List<BottomDuoRaw> createBottomDuoRows(String matchId,
      Tier tier,
      String patch,
      Map<Integer, Boolean> teamWinMap,
      TeamParticipants teamParticipants) {
    List<BottomDuoRaw> result = new ArrayList<>();

    for (Map.Entry<Integer, Boolean> e : teamWinMap.entrySet()) {
      int teamId = e.getKey();
      Integer adc = teamParticipants.adcByTeam().get(teamId);
      Integer sup = teamParticipants.supByTeam().get(teamId);
      if (adc == null || sup == null) {
        continue;
      }

      boolean win = Boolean.TRUE.equals(e.getValue());
      result.add(new BottomDuoRaw(matchId, teamId, adc, sup, win, patch, tier));
    }

    return result;
  }

  private TeamParticipants extractTeamParticipants(List<ParticipantDto> participants) {
    Map<Integer, Integer> adcByTeam = new HashMap<>();
    Map<Integer, Integer> supByTeam = new HashMap<>();

    for (ParticipantDto participant : participants) {
      if (participant == null) {
        continue;
      }

      int teamId = safeInt(participant.teamId());
      String position = safeStr(participant.individualPosition());
      int championId = safeInt(participant.championId());

      if ("BOTTOM".equals(position)) {
        adcByTeam.put(teamId, championId);
      } else if ("UTILITY".equals(position)) {
        supByTeam.put(teamId, championId);
      }
    }

    return new TeamParticipants(adcByTeam, supByTeam);
  }

  private Map<Integer, Boolean> buildTeamWinMap(List<TeamDto> teams) {
    Map<Integer, Boolean> map = new HashMap<>();
    if (teams == null) {
      return map;
    }

    for (TeamDto t : teams) {
      if (t == null) {
        continue;
      }
      int teamId = safeInt(t.teamId());
      boolean win = Boolean.TRUE.equals(t.win());
      map.put(teamId, win);
    }
    return map;
  }

  // "15.23.123.4567" -> "15.23"
  private String toPatch(String gameVersion) {
    if (gameVersion == null || gameVersion.isBlank()) {
      return null;
    }
    String[] parts = gameVersion.split("\\.");
    return (parts.length >= 2) ? parts[0] + "." + parts[1] : gameVersion;
  }

  private record TeamParticipants(Map<Integer, Integer> adcByTeam, Map<Integer, Integer> supByTeam) {}

  private int safeInt(Integer v) { return v == null ? 0 : v; }
  private String safeStr(String v) { return v == null ? "" : v; }
}
