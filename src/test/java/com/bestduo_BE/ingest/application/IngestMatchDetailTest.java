package com.bestduo_BE.ingest.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.bestduo_BE.ingest.application.port.BottomDuoRawSaver;
import com.bestduo_BE.ingest.application.port.MatchSaver;
import com.bestduo_BE.ingest.application.port.RiotMatchLoader;
import com.bestduo_BE.common.application.port.SummonerExpandQueue;
import com.bestduo_BE.common.domain.model.BottomDuoRaw;
import com.bestduo_BE.common.domain.model.IngestResult;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.riot.dto.InfoDto;
import com.bestduo_BE.common.infra.riot.dto.MetadataDto;
import com.bestduo_BE.common.infra.riot.dto.ParticipantDto;
import com.bestduo_BE.common.infra.riot.dto.RiotMatchDto;
import com.bestduo_BE.common.infra.riot.dto.TeamDto;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IngestMatchDetailTest {

  @Mock
  private RiotMatchLoader riotMatchLoader;

  @Mock
  private MatchSaver matchSaver;

  @Mock
  private BottomDuoRawSaver bottomDuoRawSaver;

  @Mock
  private SummonerExpandQueue summonerExpandQueue;

  private IngestMatchDetail useCase;

  @BeforeEach
  void setUp() {
    useCase = new IngestMatchDetail(
        riotMatchLoader,
        matchSaver,
        bottomDuoRawSaver,
        summonerExpandQueue
    );
  }

  @Test
  void saveMatchRawAndExpandParticipants() {
    RiotMatchDto match = sampleMatch();
    given(riotMatchLoader.loadMatch("KR_1")).willReturn(match);
    given(summonerExpandQueue.registerIfAbsent("puuid-1")).willReturn(true);
    given(summonerExpandQueue.registerIfAbsent("puuid-2")).willReturn(false);
    given(summonerExpandQueue.registerIfAbsent("puuid-3")).willReturn(true);

    IngestResult result = useCase.execute("KR_1", Tier.EMERALD);

    verify(matchSaver).save("KR_1", match);
    ArgumentCaptor<List<BottomDuoRaw>> rawsCaptor = ArgumentCaptor.forClass(List.class);
    verify(bottomDuoRawSaver).saveAllIdempotent(rawsCaptor.capture());
    List<BottomDuoRaw> raws = rawsCaptor.getValue();
    assertThat(raws).hasSize(2);
    assertThat(raws).allMatch(raw -> raw.tier() == Tier.EMERALD && raw.matchId().equals("KR_1"));

    verify(summonerExpandQueue).registerIfAbsent("puuid-1");
    verify(summonerExpandQueue).registerIfAbsent("puuid-2");
    verify(summonerExpandQueue).registerIfAbsent("puuid-3");

    assertThat(result.rawCreated()).isEqualTo(2);
    assertThat(result.matchStartTimeSec()).isEqualTo(12345L);
  }

  private RiotMatchDto sampleMatch() {
    List<ParticipantDto> participants = List.of(
        participant(100, "BOTTOM", 222),
        participant(100, "UTILITY", 412),
        participant(200, "BOTTOM", 51),
        participant(200, "UTILITY", 201)
    );
    List<TeamDto> teams = List.of(new TeamDto(100, true), new TeamDto(200, false));
    InfoDto info = new InfoDto(
        null, null, null, null, null, null,
        12_345_000L,
        null,
        "15.23.1",
        null, null, null,
        participants,
        teams,
        null
    );
    List<String> metadataParticipants = new ArrayList<>(List.of(" puuid-1 ", "puuid-2", "puuid-3"));
    metadataParticipants.add(null);
    MetadataDto metadata = new MetadataDto("1", "KR_1", metadataParticipants);
    return new RiotMatchDto(metadata, info);
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
