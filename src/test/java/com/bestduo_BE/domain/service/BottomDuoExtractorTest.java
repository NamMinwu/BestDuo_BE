package com.bestduo_BE.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestduo_BE.domain.model.BottomDuoRaw;
import com.bestduo_BE.domain.model.Tier;
import com.bestduo_BE.infra.riot.dto.InfoDto;
import com.bestduo_BE.infra.riot.dto.ParticipantDto;
import com.bestduo_BE.infra.riot.dto.RiotMatchDto;
import com.bestduo_BE.infra.riot.dto.TeamDto;
import java.util.List;
import org.junit.jupiter.api.Test;

class BottomDuoExtractorTest {

  private final BottomDuoExtractor extractor = new BottomDuoExtractor();

  @Test
  void extract_returnsBottomDuoRows_whenTeamsHaveBottomAndSupport() {
    List<ParticipantDto> participants = List.of(
        participant(100, "BOTTOM", 222),
        participant(100, "UTILITY", 412),
        participant(200, "BOTTOM", 51),
        participant(200, "UTILITY", 201)
    );
    List<TeamDto> teams = List.of(new TeamDto(100, true), new TeamDto(200, false));
    RiotMatchDto match = new RiotMatchDto(null, info("15.23.456.789", participants, teams));

    List<BottomDuoRaw> result = extractor.extract("KR_1", match, Tier.EMERALD);

    assertEquals(2, result.size());
    assertTrue(result.contains(new BottomDuoRaw("KR_1", 100, 222, 412, true, "15.23", Tier.EMERALD)));
    assertTrue(result.contains(new BottomDuoRaw("KR_1", 200, 51, 201, false, "15.23", Tier.EMERALD)));

  }

  @Test
  void extract_returnsEmptyList_whenMatchOrInfoMissing() {
    assertTrue(extractor.extract("KR_1", null, Tier.GOLD).isEmpty());
    assertTrue(extractor.extract("KR_1", new RiotMatchDto(null, null), Tier.GOLD).isEmpty());
  }

  @Test
  void extract_skipsTeamsWithoutBothRoles() {
    List<ParticipantDto> participants = List.of(
        participant(100, "BOTTOM", 222),
        participant(200, "UTILITY", 412)
    );
    List<TeamDto> teams = List.of(new TeamDto(100, true), new TeamDto(200, true));
    RiotMatchDto match = new RiotMatchDto(null, info("16.1", participants, teams));

    assertTrue(extractor.extract("KR_2", match, Tier.DIAMOND).isEmpty());
  }

  private InfoDto info(String gameVersion, List<ParticipantDto> participants, List<TeamDto> teams) {
    return new InfoDto(
        null, null, null, null, null, null, null, null,
        gameVersion,
        null, null, null,
        participants,
        teams,
        null
    );
  }

  private ParticipantDto participant(int teamId, String position, int championId) {
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
}
