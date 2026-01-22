package com.bestduo_BE.infra.riot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestduo_BE.domain.model.BottomDuoMatch;
import com.bestduo_BE.domain.model.Tier;
import com.bestduo_BE.infra.riot.dto.InfoDto;
import com.bestduo_BE.infra.riot.dto.MetadataDto;
import com.bestduo_BE.infra.riot.dto.ParticipantDto;
import com.bestduo_BE.infra.riot.dto.RiotMatchDto;
import java.util.List;
import org.junit.jupiter.api.Test;

class RawMatchProcessorTest {

  private final RawMatchProcessor processor = new RawMatchProcessor();

  @Test
  void returnsMatchesForTwoCompleteTeams() {
    List<String> puuids = List.of("puuid-1", "puuid-2", "puuid-3", "puuid-4");
    RiotMatchDto matchDto = matchDto(
        puuids,
        participant(100, 51, "BOTTOM", null, null, true),
        participant(100, 52, "UTILITY", null, null, true),
        participant(200, 61, "BOTTOM", null, null, false),
        participant(200, 62, "UTILITY", null, null, false)
    );

    List<BottomDuoMatch> matches = processor.getMatches(matchDto);

    assertEquals(2, matches.size());
    assertTrue(matches.stream().anyMatch(match ->
        match.getAdChampionId().equals("51")
            && match.getSupChampionId().equals("52")
            && match.getOpponentAdChampionId().equals("61")
            && match.getOpponentSupChampionId().equals("62")
            && match.getTier() == Tier.ALL_TIERS
            && match.isWins()
    ));
    assertTrue(matches.stream().anyMatch(match ->
        match.getAdChampionId().equals("61")
            && match.getSupChampionId().equals("62")
            && match.getOpponentAdChampionId().equals("51")
            && match.getOpponentSupChampionId().equals("52")
            && match.getTier() == Tier.ALL_TIERS
            && !match.isWins()
    ));
  }

  @Test
  void returnsEmptyWhenAnyTeamIsIncomplete() {
    RiotMatchDto matchDto = matchDto(
        participant(100, 1, "BOTTOM", null, null, true),
        participant(100, 2, "UTILITY", null, null, true),
        participant(200, 3, "BOTTOM", null, null, false)
    );

    List<BottomDuoMatch> matches = processor.getMatches(matchDto);

    assertTrue(matches.isEmpty());
  }

  @Test
  void infersPositionsFromRoleAndLaneAndFallsBackToSupportWinFlag() {
    ParticipantDto blueAd = participant(100, 101, null, null, "DUO_CARRY", null);
    ParticipantDto blueSup = participant(100, 102, null, "utility", null, true);
    ParticipantDto redAd = participant(200, 201, null, "bottom", null, false);
    ParticipantDto redSup = participant(200, 202, null, null, "DUO_SUPPORT", false);

    RiotMatchDto matchDto = matchDto(blueAd, blueSup, redAd, redSup);

    List<BottomDuoMatch> matches = processor.getMatches(matchDto);

    assertEquals(2, matches.size());
    assertTrue(matches.stream().anyMatch(match ->
        match.getAdChampionId().equals("101")
            && match.getSupChampionId().equals("102")
            && match.isWins()
    ));
  }

  private RiotMatchDto matchDto(ParticipantDto... participants) {
    return matchDto(List.of(), participants);
  }

  private RiotMatchDto matchDto(List<String> puuids, ParticipantDto... participants) {
    return new RiotMatchDto(new MetadataDto(null, null, puuids), new InfoDto(
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        List.of(participants),
        null,
        null
    ));
  }

  private ParticipantDto participant(
      Integer teamId,
      Integer championId,
      String individualPosition,
      String lane,
      String role,
      Boolean win
  ) {
    return new ParticipantDto(
        null,
        null,
        null,
        null,
        null,
        null,
        championId,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        teamId,
        individualPosition,
        lane,
        role,
        win
    );
  }
}
