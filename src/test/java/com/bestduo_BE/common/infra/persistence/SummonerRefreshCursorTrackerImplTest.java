package com.bestduo_BE.common.infra.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.bestduo_BE.common.application.port.MatchPayloadReader;
import com.bestduo_BE.common.infra.persistence.entity.SummonerRefreshPendingMatch;
import com.bestduo_BE.common.infra.persistence.repository.SummonerJpaRepository;
import com.bestduo_BE.common.infra.persistence.repository.SummonerRefreshPendingMatchJpaRepository;
import com.bestduo_BE.common.infra.riot.dto.InfoDto;
import com.bestduo_BE.common.infra.riot.dto.MetadataDto;
import com.bestduo_BE.common.infra.riot.dto.RiotMatchDto;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SummonerRefreshCursorTrackerImplTest {

  @Mock
  private SummonerRefreshPendingMatchJpaRepository pendingRepository;

  @Mock
  private SummonerJpaRepository summonerJpaRepository;

  @Mock
  private MatchPayloadReader matchPayloadReader;

  private SummonerRefreshCursorTrackerImpl tracker;

  @BeforeEach
  void setUp() {
    tracker = new SummonerRefreshCursorTrackerImpl(pendingRepository, summonerJpaRepository, matchPayloadReader);
  }

  @Test
  void registerRefreshBatchAdvancesCursorWhenStoredMatchesAlreadyExist() {
    given(matchPayloadReader.read("m-1")).willReturn(match(11_000L));
    given(matchPayloadReader.read("m-2")).willReturn(match(9_000L));
    given(pendingRepository.findByIdPuuidOrderByResponseIndexAsc("p1"))
        .willReturn(List.of(
            SummonerRefreshPendingMatch.newPending("p1", "m-1", 0, 11L),
            SummonerRefreshPendingMatch.newPending("p1", "m-2", 1, 9L)
        ));

    Long safeCursor = tracker.registerRefreshBatch("p1", List.of("m-1", "m-2"), 5L);

    verify(summonerJpaRepository).advanceLastMatchStartTime("p1", 11L);
    assertThat(safeCursor).isEqualTo(11L);
  }

  @Test
  void confirmMatchDoesNotAdvanceCursorUntilOldestPendingMatchIsConfirmed() {
    SummonerRefreshPendingMatch newer = SummonerRefreshPendingMatch.newPending("p1", "m-new", 0, null);
    SummonerRefreshPendingMatch older = SummonerRefreshPendingMatch.newPending("p1", "m-old", 1, null);
    given(pendingRepository.findByIdMatchId("m-new")).willReturn(List.of(newer));
    given(pendingRepository.findByIdPuuidOrderByResponseIndexAsc("p1"))
        .willReturn(List.of(newer, older));

    tracker.confirmMatchIngested("m-new", 20L);

    verify(summonerJpaRepository, never()).advanceLastMatchStartTime("p1", 20L);
  }

  @Test
  void confirmMatchAdvancesCursorForOldestContiguousConfirmedSuffix() {
    SummonerRefreshPendingMatch newer = SummonerRefreshPendingMatch.newPending("p1", "m-new", 0, 20L);
    SummonerRefreshPendingMatch older = SummonerRefreshPendingMatch.newPending("p1", "m-old", 1, null);
    given(pendingRepository.findByIdMatchId("m-old")).willReturn(List.of(older));
    given(pendingRepository.findByIdPuuidOrderByResponseIndexAsc("p1"))
        .willReturn(List.of(newer, older));

    tracker.confirmMatchIngested("m-old", 10L);

    verify(summonerJpaRepository).advanceLastMatchStartTime("p1", 20L);
  }

  @Test
  void oneConfirmedMatchCanAdvanceMultipleSummoners() {
    SummonerRefreshPendingMatch row1 = SummonerRefreshPendingMatch.newPending("p1", "m-shared", 0, null);
    SummonerRefreshPendingMatch row2 = SummonerRefreshPendingMatch.newPending("p2", "m-shared", 0, null);
    given(pendingRepository.findByIdMatchId("m-shared")).willReturn(List.of(row1, row2));
    given(pendingRepository.findByIdPuuidOrderByResponseIndexAsc("p1")).willReturn(List.of(row1));
    given(pendingRepository.findByIdPuuidOrderByResponseIndexAsc("p2")).willReturn(List.of(row2));

    tracker.confirmMatchIngested("m-shared", 15L);

    verify(summonerJpaRepository).advanceLastMatchStartTime("p1", 15L);
    verify(summonerJpaRepository).advanceLastMatchStartTime("p2", 15L);
  }

  private RiotMatchDto match(long gameStartMs) {
    return new RiotMatchDto(new MetadataDto("1", "KR_1", List.of()), new InfoDto(
        null, null, null, null, null, null,
        gameStartMs,
        null,
        null,
        null, null, null,
        null,
        null,
        null
    ));
  }
}
