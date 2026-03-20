package com.bestduo_BE.aggregate.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bestduo_BE.aggregate.infra.persistence.entity.BottomDuoStatAgg;
import com.bestduo_BE.aggregate.infra.persistence.repository.BottomDuoStatAggJpaRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ComputeBottomDuoRankingTest {

  @Mock
  private BottomDuoStatAggJpaRepository repository;

  private ComputeBottomDuoRanking useCase;

  @BeforeEach
  void setUp() {
    useCase = new ComputeBottomDuoRanking(repository);
  }

  @Test
  void returnZeroWhenNoPatchDataExists() {
    when(repository.findLatestPatchVersion()).thenReturn(null);

    ComputeBottomDuoRanking.Result result = useCase.execute();

    assertThat(result.patchVersion()).isNull();
    assertThat(result.updatedRows()).isZero();
    verify(repository, never()).saveAll(anyList());
  }

  @Test
  void computeRankingAndApplyPreviousPatchDelta() {
    OffsetDateTime now = OffsetDateTime.now();
    BottomDuoStatAgg asheLux = BottomDuoStatAgg.builder()
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
        .rankingStatus(BottomDuoStatAgg.RankingStatus.PENDING)
        .createdAt(now)
        .updatedAt(now)
        .build();
    BottomDuoStatAgg jinxThresh = BottomDuoStatAgg.builder()
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
        .rankingStatus(BottomDuoStatAgg.RankingStatus.PENDING)
        .createdAt(now)
        .updatedAt(now)
        .build();

    BottomDuoStatAgg prevAsheLux = BottomDuoStatAgg.builder()
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
        .rankingStatus(BottomDuoStatAgg.RankingStatus.RANKED)
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
    assertThat(asheLux.getRankingStatus()).isEqualTo(BottomDuoStatAgg.RankingStatus.RANKED);
    assertThat(jinxThresh.getRanking()).isEqualTo(2);
    assertThat(jinxThresh.getPreviousRanking()).isNull();
    assertThat(jinxThresh.getRankingStatus()).isEqualTo(BottomDuoStatAgg.RankingStatus.RANKED);
  }

  @Test
  void markInsufficientDataWhenGamesBelowThreshold() {
    OffsetDateTime now = OffsetDateTime.now();
    BottomDuoStatAgg lowSample = BottomDuoStatAgg.builder()
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
        .rankingStatus(BottomDuoStatAgg.RankingStatus.PENDING)
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
    assertThat(lowSample.getRankingStatus()).isEqualTo(BottomDuoStatAgg.RankingStatus.INSUFFICIENT);
  }
}
