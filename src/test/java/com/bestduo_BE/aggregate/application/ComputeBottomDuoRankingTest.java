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
  @DisplayName("execute — patchVersion이 null이면 즉시 0을 반환한다")
  void returnZeroWhenPatchVersionIsNull() {
    ComputeBottomDuoRanking.Result result = useCase.execute(null, null);

    assertThat(result.patchVersion()).isNull();
    assertThat(result.updatedRows()).isZero();
    verify(repository, never()).saveAll(anyList());
  }

  @Test
  @DisplayName("execute — 해당 patch에 데이터가 없으면 0을 반환한다")
  void returnZeroWhenNoPatchDataExists() {
    when(repository.findByPatchVersion("14.10")).thenReturn(List.of());

    ComputeBottomDuoRanking.Result result = useCase.execute("14.10", null);

    assertThat(result.patchVersion()).isEqualTo("14.10");
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

    when(repository.findByPatchVersion("14.10"))
        .thenReturn(List.of(asheLux, jinxThresh));
    when(repository.findPreviousPatchVersion("14.10"))
        .thenReturn("14.9");
    when(repository.findByPatchVersion("14.9"))
        .thenReturn(List.of(prevAsheLux));

    ComputeBottomDuoRanking.Result result = useCase.execute("14.10", null);

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

    when(repository.findByPatchVersion("14.10"))
        .thenReturn(List.of(lowSample));
    when(repository.findPreviousPatchVersion("14.10")).thenReturn(null);

    ComputeBottomDuoRanking.Result result = useCase.execute("14.10", null);

    assertThat(result.updatedRows()).isEqualTo(1);
    verify(repository).saveAll(List.of(lowSample));
    assertThat(lowSample.getRanking()).isNull();
    assertThat(lowSample.getDuoTier()).isEqualTo(5);
    assertThat(lowSample.getRankingStatus()).isEqualTo(BottomDuoStatAggregate.RankingStatus.INSUFFICIENT);
  }

  @Test
  @DisplayName("execute — 저샘플 고승률 듀오는 대량 견조 듀오보다 낮은 랭킹을 가진다")
  void lowSampleHighWinrate_ranksBelow_highSampleSteady() {
    OffsetDateTime now = OffsetDateTime.now();
    BottomDuoStatAggregate lowSampleHighWr = builder("LowAdc", "LowSup", 8, 7, 0.875, 0.528, now);
    BottomDuoStatAggregate highSampleSteady = builder("BigAdc", "BigSup", 2700, 1485, 0.55, 0.546, now);

    when(repository.findByPatchVersion("14.10"))
        .thenReturn(List.of(lowSampleHighWr, highSampleSteady));
    when(repository.findPreviousPatchVersion("14.10")).thenReturn(null);

    useCase.execute("14.10", null);

    assertThat(highSampleSteady.getRanking()).isEqualTo(1);
    assertThat(lowSampleHighWr.getRanking()).isEqualTo(2);
  }

  @Test
  @DisplayName("execute — 대량 평균 듀오가 중샘플 강함 듀오보다 높은 랭킹을 가진다")
  void highSampleAverage_ranksAbove_midSampleStrong() {
    OffsetDateTime now = OffsetDateTime.now();
    BottomDuoStatAggregate bigAvg = builder("AvgAdc", "AvgSup", 1000, 500, 0.50, 0.500, now);
    BottomDuoStatAggregate midStrong = builder("MidAdc", "MidSup", 100, 65, 0.65, 0.575, now);

    when(repository.findByPatchVersion("14.10"))
        .thenReturn(List.of(bigAvg, midStrong));
    when(repository.findPreviousPatchVersion("14.10")).thenReturn(null);

    useCase.execute("14.10", null);

    assertThat(bigAvg.getRanking()).isEqualTo(1);
    assertThat(midStrong.getRanking()).isEqualTo(2);
  }

  private static BottomDuoStatAggregate builder(String adc, String sup, int games, int wins,
      double winRate, double adjustedWinRate, OffsetDateTime now) {
    return BottomDuoStatAggregate.builder()
        .patchVersion("14.10")
        .adcChampionId(adc)
        .supChampionId(sup)
        .tier("EMERALD")
        .wins(wins)
        .games(games)
        .winRate(winRate)
        .pickRate(0)
        .adjustedWinRate(adjustedWinRate)
        .rankScore(0)
        .ranking(null)
        .duoTier(null)
        .previousRanking(null)
        .rankDelta(null)
        .rankingStatus(BottomDuoStatAggregate.RankingStatus.PENDING)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }

  @Test
  @DisplayName("execute — tier가 지정되면 tier 범위로 필터링한다")
  void executeFiltersByTierWhenTierScopeProvided() {
    OffsetDateTime now = OffsetDateTime.now();
    BottomDuoStatAggregate emeraldOnly = BottomDuoStatAggregate.builder()
        .patchVersion("14.10")
        .adcChampionId("Caitlyn")
        .supChampionId("Morgana")
        .tier("EMERALD")
        .wins(30)
        .games(50)
        .winRate(0.6)
        .pickRate(0)
        .adjustedWinRate(0.58)
        .rankScore(0)
        .ranking(null)
        .duoTier(null)
        .previousRanking(null)
        .rankDelta(null)
        .rankingStatus(BottomDuoStatAggregate.RankingStatus.PENDING)
        .createdAt(now)
        .updatedAt(now)
        .build();

    when(repository.findByPatchVersionAndTier("14.10", "EMERALD"))
        .thenReturn(List.of(emeraldOnly));
    when(repository.findPreviousPatchVersion("14.10")).thenReturn(null);

    ComputeBottomDuoRanking.Result result = useCase.execute("14.10", "EMERALD");

    assertThat(result.patchVersion()).isEqualTo("14.10");
    assertThat(result.updatedRows()).isEqualTo(1);
    verify(repository).saveAll(List.of(emeraldOnly));
    assertThat(emeraldOnly.getRanking()).isEqualTo(1);
  }
}
