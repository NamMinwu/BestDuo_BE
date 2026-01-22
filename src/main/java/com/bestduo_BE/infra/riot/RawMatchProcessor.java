package com.bestduo_BE.infra.riot;

import com.bestduo_BE.domain.model.BottomDuoMatch;
import com.bestduo_BE.domain.model.Tier;
import com.bestduo_BE.infra.riot.dto.ParticipantDto;
import com.bestduo_BE.infra.riot.dto.RiotMatchDto;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RawMatchProcessor {

  public List<BottomDuoMatch> getMatches(RiotMatchDto matchDto) {
    if (matchDto == null || matchDto.info() == null
        || matchDto.info().participants() == null
        || matchDto.info().participants().isEmpty()) {
      return List.of();
    }

    Map<Integer, TeamDuo> teamDuos = buildTeamDuos(matchDto.info().participants());
    List<Map.Entry<Integer, TeamDuo>> completedTeams = teamDuos.entrySet().stream()
        .filter(entry -> entry.getValue().isComplete())
        .toList();

    if (completedTeams.size() < 2) {
      return List.of();
    }

    Map.Entry<Integer, TeamDuo> first = completedTeams.get(0);
    Map.Entry<Integer, TeamDuo> second = completedTeams.get(1);

    List<BottomDuoMatch> matches = new ArrayList<>();
    addMatch(matches, first.getValue(), second.getValue());
    addMatch(matches, second.getValue(), first.getValue());

    return matches.isEmpty() ? List.of() : List.copyOf(matches);
  }

  private Map<Integer, TeamDuo> buildTeamDuos(List<ParticipantDto> participants) {
    Map<Integer, TeamDuo> teamDuos = new HashMap<>();
    for (ParticipantDto participant : participants) {
      if (participant == null || participant.teamId() == null) {
        continue;
      }
      teamDuos
          .computeIfAbsent(participant.teamId(), ignored -> new TeamDuo())
          .accept(participant);
    }
    return teamDuos;
  }

  private void addMatch(List<BottomDuoMatch> matches, TeamDuo myTeam, TeamDuo opponentTeam) {
    if (!myTeam.isComplete() || !opponentTeam.isComplete()) {
      return;
    }

    String adId = championId(myTeam.ad);
    String supId = championId(myTeam.sup);
    String opponentAdId = championId(opponentTeam.ad);
    String opponentSupId = championId(opponentTeam.sup);

    if (adId == null || supId == null || opponentAdId == null || opponentSupId == null) {
      return;
    }

    matches.add(new BottomDuoMatch(
        adId,
        supId,
        opponentAdId,
        opponentSupId,
        Tier.ALL_TIERS,
        resolveWin(myTeam)
    ));
  }

  private boolean resolveWin(TeamDuo teamDuo) {
    Boolean win = teamDuo.ad.win() != null ? teamDuo.ad.win() : teamDuo.sup.win();
    return Boolean.TRUE.equals(win);
  }

  private String championId(ParticipantDto participant) {
    return participant.championId() == null ? null : String.valueOf(participant.championId());
  }

  private static class TeamDuo {
    private ParticipantDto ad;
    private ParticipantDto sup;

    void accept(ParticipantDto participant) {
      String position = resolvePosition(participant);
      if (position == null) {
        return;
      }

      if ("BOTTOM".equals(position) || "DUO_CARRY".equals(position)) {
        ad = selectMoreRelevant(ad, participant);
        return;
      }

      if ("UTILITY".equals(position) || "DUO_SUPPORT".equals(position)) {
        sup = selectMoreRelevant(sup, participant);
      }
    }

    private ParticipantDto selectMoreRelevant(ParticipantDto current, ParticipantDto candidate) {
      return current == null ? candidate : current;
    }

    private String resolvePosition(ParticipantDto participant) {
      if (participant.individualPosition() != null && !participant.individualPosition().isBlank()) {
        return participant.individualPosition().toUpperCase();
      }
      if (participant.role() != null && !participant.role().isBlank()) {
        return participant.role().toUpperCase();
      }
      if (participant.lane() != null && !participant.lane().isBlank()) {
        return participant.lane().toUpperCase();
      }
      return null;
    }

    boolean isComplete() {
      return ad != null && sup != null;
    }
  }
}
