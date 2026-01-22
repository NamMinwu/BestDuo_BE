package com.bestduo_BE.domain.service;

import com.bestduo_BE.domain.model.BottomDuoRaw;
import com.bestduo_BE.domain.model.Tier;
import com.bestduo_BE.infra.riot.dto.InfoDto;
import com.bestduo_BE.infra.riot.dto.ParticipantDto;
import com.bestduo_BE.infra.riot.dto.RiotMatchDto;
import com.bestduo_BE.infra.riot.dto.TeamDto;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BottomDuoExtractor {

  public List<BottomDuoRaw> extract(String matchId, RiotMatchDto match, Tier tier) {
    if (match == null || match.info() == null) return List.of();

    InfoDto info = match.info();

    String patch = toPatch(info.gameVersion());
    Map<Integer, Boolean> teamWinMap = buildTeamWinMap(info.teams());

    Map<Integer, Integer> adcByTeam = new HashMap<>();
    Map<Integer, Integer> supByTeam = new HashMap<>();

    if (info.participants() == null) return List.of();

    for (ParticipantDto p : info.participants()) {
      if (p == null) continue;

      int teamId = safeInt(p.teamId());
      String pos = safeStr(p.individualPosition());
      int champId = safeInt(p.championId());

      if ("BOTTOM".equals(pos)) adcByTeam.put(teamId, champId);
      else if ("UTILITY".equals(pos)) supByTeam.put(teamId, champId);
    }

    List<BottomDuoRaw> result = new ArrayList<>();
    for (Map.Entry<Integer, Boolean> e : teamWinMap.entrySet()) {
      int teamId = e.getKey();

      Integer adc = adcByTeam.get(teamId);
      Integer sup = supByTeam.get(teamId);
      if (adc == null || sup == null) continue; // Phase1: 없으면 스킵

      boolean win = Boolean.TRUE.equals(e.getValue());
      result.add(new BottomDuoRaw(matchId, teamId, adc, sup, win, patch, tier));
    }
    return result;
  }

  private Map<Integer, Boolean> buildTeamWinMap(List<TeamDto> teams) {
    Map<Integer, Boolean> map = new HashMap<>();
    if (teams == null) return map;

    for (TeamDto t : teams) {
      if (t == null) continue;
      int teamId = safeInt(t.teamId());
      boolean win = Boolean.TRUE.equals(t.win());
      map.put(teamId, win);
    }
    return map;
  }

  // "15.23.123.4567" -> "15.23"
  private String toPatch(String gameVersion) {
    if (gameVersion == null || gameVersion.isBlank()) return null;
    String[] parts = gameVersion.split("\\.");
    return (parts.length >= 2) ? parts[0] + "." + parts[1] : gameVersion;
  }

  private int safeInt(Integer v) { return v == null ? 0 : v; }
  private String safeStr(String v) { return v == null ? "" : v; }
}
