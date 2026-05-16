package com.bestduo_BE.ingest.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.bestduo_BE.common.application.port.RiotApiPort;
import com.bestduo_BE.ingest.infra.persistence.BottomDuoRawSaver;
import com.bestduo_BE.ingest.infra.persistence.MatchSaver;
import com.bestduo_BE.common.domain.model.BottomDuoRaw;
import com.bestduo_BE.common.domain.model.IngestResult;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.domain.service.BottomDuoExtractor;
import com.bestduo_BE.common.infra.riot.dto.InfoDto;
import com.bestduo_BE.common.infra.riot.dto.MetadataDto;
import com.bestduo_BE.common.infra.riot.dto.ParticipantDto;
import com.bestduo_BE.common.infra.riot.dto.RiotMatchDto;
import com.bestduo_BE.common.infra.riot.dto.TeamDto;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IngestMatchDetailTest {

  @Mock
  private RiotApiPort riotApiPort;

  @Mock
  private MatchSaver matchSaver;

  @Mock
  private BottomDuoRawSaver bottomDuoRawSaver;

  private IngestMatchDetail useCase;

  @BeforeEach
  void setUp() {
    useCase = new IngestMatchDetail(riotApiPort, matchSaver, bottomDuoRawSaver, new BottomDuoExtractor());
  }

  @Test
  @DisplayName("match 저장, bottom duo raw 저장, 결과 반환")
  void saveMatchAndBottomDuoRaws() {
    RiotMatchDto match = sampleMatch();
    given(riotApiPort.loadMatch("KR_1")).willReturn(match);

    IngestResult result = useCase.execute("KR_1", Tier.EMERALD, null);

    verify(matchSaver).save("KR_1", match, Tier.EMERALD);
    ArgumentCaptor<List<BottomDuoRaw>> rawsCaptor = ArgumentCaptor.forClass(List.class);
    verify(bottomDuoRawSaver).saveAllIdempotent(rawsCaptor.capture());
    List<BottomDuoRaw> raws = rawsCaptor.getValue();
    assertThat(raws).hasSize(2);
    assertThat(raws).allMatch(raw -> raw.tier() == Tier.EMERALD && raw.matchId().equals("KR_1"));

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
    MetadataDto metadata = new MetadataDto("1", "KR_1", List.of("puuid-1", "puuid-2", "puuid-3"));
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
