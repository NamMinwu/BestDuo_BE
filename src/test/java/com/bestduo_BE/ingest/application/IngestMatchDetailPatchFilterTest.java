package com.bestduo_BE.ingest.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.bestduo_BE.common.domain.model.BottomDuoRaw;
import com.bestduo_BE.common.domain.model.IngestResult;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.domain.service.BottomDuoExtractor;
import com.bestduo_BE.common.infra.riot.dto.InfoDto;
import com.bestduo_BE.common.infra.riot.dto.MetadataDto;
import com.bestduo_BE.common.infra.riot.dto.ParticipantDto;
import com.bestduo_BE.common.infra.riot.dto.RiotMatchDto;
import com.bestduo_BE.common.infra.riot.dto.TeamDto;
import com.bestduo_BE.common.application.port.RiotApiPort;
import com.bestduo_BE.ingest.infra.persistence.BottomDuoRawSaver;
import com.bestduo_BE.ingest.infra.persistence.MatchSaver;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IngestMatchDetailPatchFilterTest {

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
  @DisplayName("expectedPatch 일치 시 모든 raw 저장")
  void execute_withMatchingPatch_savesAllRaws() {
    RiotMatchDto match = sampleMatchWithGameVersion("15.23.1");
    given(riotApiPort.loadMatch("KR_1")).willReturn(match);

    IngestResult result = useCase.execute("KR_1", Tier.GOLD, "15.23");

    ArgumentCaptor<List<BottomDuoRaw>> captor = ArgumentCaptor.forClass(List.class);
    verify(bottomDuoRawSaver).saveAllIdempotent(captor.capture());
    assertThat(captor.getValue()).hasSize(2);
    assertThat(result.rawCreated()).isEqualTo(2);
  }

  @Test
  @DisplayName("expectedPatch 불일치 시 match 저장 스킵, raw 저장 스킵")
  void execute_withMismatchedPatch_skipsMatchAndRawSave() {
    RiotMatchDto match = sampleMatchWithGameVersion("15.22.1");  // 이전 패치
    given(riotApiPort.loadMatch("KR_2")).willReturn(match);

    IngestResult result = useCase.execute("KR_2", Tier.GOLD, "15.23");

    verifyNoInteractions(matchSaver);
    verify(bottomDuoRawSaver, never()).saveAllIdempotent(any());
    assertThat(result.rawCreated()).isEqualTo(0);
  }

  @Test
  @DisplayName("expectedPatch null이면 필터링 없이 전체 저장 (하위 호환)")
  void execute_withNullExpectedPatch_savesAllRawsWithoutFiltering() {
    RiotMatchDto match = sampleMatchWithGameVersion("15.22.1");
    given(riotApiPort.loadMatch("KR_3")).willReturn(match);

    IngestResult result = useCase.execute("KR_3", Tier.GOLD, null);

    ArgumentCaptor<List<BottomDuoRaw>> captor = ArgumentCaptor.forClass(List.class);
    verify(bottomDuoRawSaver).saveAllIdempotent(captor.capture());
    assertThat(captor.getValue()).hasSize(2);
    assertThat(result.rawCreated()).isEqualTo(2);
  }

  private RiotMatchDto sampleMatchWithGameVersion(String gameVersion) {
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
        gameVersion,
        null, null, null,
        participants,
        teams,
        null
    );
    MetadataDto metadata = new MetadataDto("1", "KR_1", List.of("puuid-1"));
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
