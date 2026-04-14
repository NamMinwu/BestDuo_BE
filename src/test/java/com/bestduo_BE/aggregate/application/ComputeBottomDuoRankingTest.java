package com.bestduo_BE.aggregate.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bestduo_BE.aggregate.infra.persistence.entity.BottomDuoStatAggregate;
import com.bestduo_BE.aggregate.infra.persistence.repository.BottomDuoStatAggregateJpaRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ComputeBottomDuoRankingTest {

  @Mock
  private BottomDuoStatAggregateJpaRepository repository;

  private ComputeBottomDuoRanking useCase;

  @BeforeEach
  void setUp() {
    useCase = new ComputeBottomDuoRanking(repository);
  }

  @Test
  @DisplayName("execute — 패치 데이터가 없으면 0을 반환한다")
  void returnZeroWhenNoPatchDataExists() {
    when(repository.findLatestPatchVersion()).thenReturn(null);

    ComputeBottomDuoRanking.Result result = useCase.execute();

    assertThat(result.patchVersion()).isNull();
    assertThat(result.updatedRows()).isZero();
    verify(repository, never()).saveAll(anyList());
  }

  @Test
  @DisplayName("execute — 랭킹을 계산하고 이전 패치 대비 델타를 적용한다")
  void computeRankingAndApplyPreviousPatchDelta() {
    OffsetDateTime now = OffsetDateTime.now();
    BottomDuoStatAggregate asheLux = BottomDuoStatAggregate.builder()
        .patchVersion("14.10")
        .adcChampionId("Ashe")
        .supChampionId("Lux")
        .tier("EMERALD")
        .wins(40)
        .games(60)
        .winRate(0.66)
        .pickRate(0)
        .adjustedWinRate(0.64)
        .rankScore(0)
        .ranking(null)
        .duoTier(null)
        .previousRanking(null)
        .rankDelta(null)
        .rankingStatus(BottomDuoStatAggregate.RankingStatus.PENDING)
        .createdAt(now)
        .updatedAt(now)
        .build();
    BottomDuoStatAggregate jinxThresh = BottomDuoStatAggregate.builder()
        .patchVersion("14.10")
        .adcChampionId("Jinx")
        .supChampionId("Thresh")
        .tier("EMERALD")
        .wins(35)
        .games(50)
        .winRate(0.6)
        .pickRate(0)
        .adjustedWinRate(0.6)
        .rankScore(0)
        .ranking(null)
        .duoTier(null)
        .previousRanking(null)
        .rankDelta(null)
        .rankingStatus(BottomDuoStatAggregate.RankingStatus.PENDING)
        .createdAt(now)
        .updatedAt(now)
        .build();

    BottomDuoStatAggregate prevAsheLux = BottomDuoStatAggregate.builder()
        .patchVersion("14.9")
        .adcChampionId("Ashe")
        .supChampionId("Lux")
        .tier("EMERALD")
        .wins(10)
        .games(20)
        .winRate(0.5)
        .pickRate(0)
        .adjustedWinRate(0.5)
        .rankScore(0)
        .ranking(4)
        .duoTier(2)
        .previousRanking(null)
        .rankDelta(null)
        .rankingStatus(BottomDuoStatAggregate.RankingStatus.RANKED)
        .createdAt(now.minusDays(10))
        .updatedAt(now.minusDays(5))
        .build();

    when(repository.findLatestPatchVersion()).thenReturn("14.10");
    when(repository.findByPatchVersion("14.10"))
        .thenReturn(List.of(asheLux, jinxThresh));
    when(repository.findPreviousPatchVersion("14.10"))
        .thenReturn("14.9");
    when(repository.findByPatchVersion("14.9"))
        .thenReturn(List.of(prevAsheLux));

    ComputeBottomDuoRanking.Result result = useCase.execute();

    assertThat(result.patchVersion()).isEqualTo("14.10");
    assertThat(result.updatedRows()).isEqualTo(2);

    verify(repository).saveAll(List.of(asheLux, jinxThresh));
    assertThat(asheLux.getRanking()).isEqualTo(1);
    assertThat(asheLux.getPreviousRanking()).isEqualTo(4);
    assertThat(asheLux.getRankDelta()).isEqualTo(3);
    assertThat(asheLux.getRankingStatus()).isEqualTo(BottomDuoStatAggregate.RankingStatus.RANKED);
    assertThat(jinxThresh.getRanking()).isEqualTo(2);
    assertThat(jinxThresh.getPreviousRanking()).isNull();
    assertThat(jinxThresh.getRankingStatus()).isEqualTo(BottomDuoStatAggregate.RankingStatus.RANKED);
  }

  @Test
  @DisplayName("execute — 게임 수가 임계값 미만이면 INSUFFICIENT로 표시한다")
  void markInsufficientDataWhenGamesBelowThreshold() {
    OffsetDateTime now = OffsetDateTime.now();
    BottomDuoStatAggregate lowSample = BottomDuoStatAggregate.builder()
        .patchVersion("14.10")
        .adcChampionId("Ezreal")
        .supChampionId("Yuumi")
        .tier("EMERALD")
        .wins(5)
        .games(3)
        .winRate(0.5)
        .pickRate(0)
        .adjustedWinRate(0.5)
        .rankScore(0)
        .ranking(null)
        .duoTier(null)
        .previousRanking(null)
        .rankDelta(null)
        .rankingStatus(BottomDuoStatAggregate.RankingStatus.PENDING)
        .createdAt(now)
        .updatedAt(now)
        .build();

    when(repository.findLatestPatchVersion()).thenReturn("14.10");
    when(repository.findByPatchVersion("14.10"))
        .thenReturn(List.of(lowSample));
    when(repository.findPreviousPatchVersion("14.10")).thenReturn(null);

    ComputeBottomDuoRanking.Result result = useCase.execute();

    assertThat(result.updatedRows()).isEqualTo(1);
    verify(repository).saveAll(List.of(lowSample));
    assertThat(lowSample.getRanking()).isNull();
    assertThat(lowSample.getDuoTier()).isEqualTo(5);
    assertThat(lowSample.getRankingStatus()).isEqualTo(BottomDuoStatAggregate.RankingStatus.INSUFFICIENT);
  }
}
