package com.bestduo_BE.aggregate.infra.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.aggregate.infra.persistence.entity.BottomDuoStatAggregate;
import com.bestduo_BE.aggregate.infra.persistence.repository.BottomDuoStatAggregateJpaRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BottomDuoStatFinderTest {

  @Mock
  private BottomDuoStatAggregateJpaRepository repository;

  private BottomDuoStatFinder finder;

  @BeforeEach
  void setUp() {
    finder = new BottomDuoStatFinder(repository);
  }

  @Test
  @DisplayName("findTierTotalGames — 레포지토리에 위임하고 티어 전체 게임 수를 반환한다")
  void findTierTotalGamesDelegatesToRepository() {
    when(repository.findLatestPatchVersion()).thenReturn("14.10");
    when(repository.sumGamesByTier("14.10", "GOLD")).thenReturn(128);

    int games = finder.findTierTotalGames(Tier.GOLD, null);

    assertThat(games).isEqualTo(128);
    verify(repository).findLatestPatchVersion();
    verify(repository).sumGamesByTier("14.10", "GOLD");
  }

  @Test
  @DisplayName("findTierTotalGames — 제공된 패치 버전을 우선 사용한다")
  void findTierTotalGamesUsesProvidedPatchVersion() {
    when(repository.sumGamesByTier("14.9", "GOLD")).thenReturn(222);

    int games = finder.findTierTotalGames(Tier.GOLD, "14.9");

    assertThat(games).isEqualTo(222);
    verify(repository).sumGamesByTier("14.9", "GOLD");
    verify(repository, never()).findLatestPatchVersion();
  }

  @Test
  @DisplayName("resolvePatchVersion — 명시적으로 제공된 패치 버전을 우선한다")
  void resolvePatchVersionPrefersProvidedValue() {
    String patch = finder.resolvePatchVersion(" 14.9 ");

    assertThat(patch).isEqualTo("14.9");
    verify(repository, never()).findLatestPatchVersion();
  }

  @Test
  @DisplayName("resolvePatchVersion — 패치 버전이 없으면 최신 버전으로 폴백한다")
  void resolvePatchVersionFallsBackToLatest() {
    when(repository.findLatestPatchVersion()).thenReturn("14.10");

    String patch = finder.resolvePatchVersion(null);

    assertThat(patch).isEqualTo("14.10");
    verify(repository).findLatestPatchVersion();
  }

  @Test
  @DisplayName("findStats — WINRATE_DESC 정렬 시 엔티티를 StatRow로 변환한다")
  void findStatsMapsEntitiesForWinRateDesc() {
    OffsetDateTime now = OffsetDateTime.now();
    BottomDuoStatAggregate entity = BottomDuoStatAggregate.builder()
        .patchVersion("14.10")
        .adcChampionId("Ashe")
        .supChampionId("Lux")
        .tier(Tier.EMERALD.name())
        .wins(30)
        .games(50)
        .winRate(0.6)
        .pickRate(0.1)
        .adjustedWinRate(0.55)
        .rankScore(0.0)
        .ranking(1)
        .duoTier(0)
        .previousRanking(2)
        .rankDelta(1)
        .rankingStatus(BottomDuoStatAggregate.RankingStatus.RANKED)
        .createdAt(now)
        .updatedAt(now)
        .build();

    when(repository.findLatestPatchVersion()).thenReturn("14.10");
    when(repository.findTopWinRateDesc("14.10", "EMERALD", "Ashe", "Lux", 3))
        .thenReturn(List.of(entity));

    List<BottomDuoStatFinder.StatRow> rows = finder.findStats(
        Tier.EMERALD,
        null,
        "Ashe",
        "Lux",
        BottomDuoStatFinder.SortKey.WINRATE_DESC,
        0,
        3);

    assertThat(rows).hasSize(1);
    BottomDuoStatFinder.StatRow row = rows.get(0);
    assertThat(row.adcChampionId()).isEqualTo("Ashe");
    assertThat(row.supChampionId()).isEqualTo("Lux");
    assertThat(row.tier()).isEqualTo(Tier.EMERALD);
    assertThat(row.wins()).isEqualTo(30);
    assertThat(row.games()).isEqualTo(50);
    assertThat(row.duoTier()).isEqualTo(0);
    assertThat(row.ranking()).isEqualTo(1);
    assertThat(row.rankDelta()).isEqualTo(1);
    verify(repository).findLatestPatchVersion();
    verify(repository).findTopWinRateDesc("14.10", "EMERALD", "Ashe", "Lux", 3);
  }

  @Test
  @DisplayName("findStats — 패치 버전이 있으면 레포지토리 조회를 건너뛴다")
  void findStatsUsesProvidedPatchVersionWhenPresent() {
    when(repository.findTopWinRateDesc("14.9", "GOLD", null, null, 2))
        .thenReturn(List.of());

    finder.findStats(
        Tier.GOLD,
        "14.9",
        null,
        null,
        BottomDuoStatFinder.SortKey.WINRATE_DESC,
        0,
        2);

    verify(repository).findTopWinRateDesc("14.9", "GOLD", null, null, 2);
    verify(repository, never()).findLatestPatchVersion();
  }

  @Test
  @DisplayName("findStats — 입력값을 trim하고 WINRATE_ASC 최소 limit 1을 보장한다")
  void findStatsTrimsInputsAndEnforcesMinimumLimitForWinRateAsc() {
    when(repository.findLatestPatchVersion()).thenReturn("14.10");
    when(repository.findTopWinRateAsc("14.10", "SILVER", "Caitlyn", null, 1))
        .thenReturn(List.of());

    finder.findStats(
        Tier.SILVER,
        null,
        "  Caitlyn  ",
        "   ",
        BottomDuoStatFinder.SortKey.WINRATE_ASC,
        0,
        0);

    ArgumentCaptor<String> adcCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> supCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Integer> limitCaptor = ArgumentCaptor.forClass(Integer.class);

    verify(repository).findLatestPatchVersion();
    verify(repository).findTopWinRateAsc(
        eq("14.10"),
        eq("SILVER"),
        adcCaptor.capture(),
        supCaptor.capture(),
        limitCaptor.capture());

    assertThat(adcCaptor.getValue()).isEqualTo("Caitlyn");
    assertThat(supCaptor.getValue()).isNull();
    assertThat(limitCaptor.getValue()).isEqualTo(1);
  }

  @Test
  @DisplayName("findStats — PICKRATE_DESC 정렬 시 tierTotalGames를 레포지토리에 전달한다")
  void findStatsPassesTierTotalGamesForPickRateDesc() {
    when(repository.findLatestPatchVersion()).thenReturn("14.10");
    when(repository.findTopPickRateDesc("14.10", "DIAMOND", null, null, 777, 5))
        .thenReturn(List.of());

    finder.findStats(
        Tier.DIAMOND,
        null,
        null,
        null,
        BottomDuoStatFinder.SortKey.PICKRATE_DESC,
        777,
        5);

    verify(repository).findLatestPatchVersion();
    verify(repository).findTopPickRateDesc("14.10", "DIAMOND", null, null, 777, 5);
  }

  @Test
  @DisplayName("findStats — PICKRATE_ASC 쿼리를 호출한다")
  void findStatsUsesPickRateAscQuery() {
    when(repository.findLatestPatchVersion()).thenReturn("14.10");
    when(repository.findTopPickRateAsc("14.10", "BRONZE", null, "Thresh", 321, 2))
        .thenReturn(List.of());

    finder.findStats(
        Tier.BRONZE,
        null,
        null,
        "Thresh",
        BottomDuoStatFinder.SortKey.PICKRATE_ASC,
        321,
        2);

    verify(repository).findLatestPatchVersion();
    verify(repository).findTopPickRateAsc("14.10", "BRONZE", null, "Thresh", 321, 2);
  }

  @Test
  @DisplayName("findStats — DUO_TIER_ASC 쿼리를 호출한다")
  void findStatsUsesDuoTierAscQuery() {
    when(repository.findLatestPatchVersion()).thenReturn("14.10");
    when(repository.findTopDuoTierAsc("14.10", "PLATINUM", null, null, 4))
        .thenReturn(List.of());

    finder.findStats(
        Tier.PLATINUM,
        null,
        null,
        null,
        BottomDuoStatFinder.SortKey.DUO_TIER_ASC,
        0,
        4);

    verify(repository).findLatestPatchVersion();
    verify(repository).findTopDuoTierAsc("14.10", "PLATINUM", null, null, 4);
  }

  @Test
  @DisplayName("findStats — RANKING_DESC 쿼리를 호출한다")
  void findStatsUsesRankingDescQuery() {
    when(repository.findLatestPatchVersion()).thenReturn("14.10");
    when(repository.findTopRankingDesc("14.10", "GOLD", "Senna", null, 6))
        .thenReturn(List.of());

    finder.findStats(
        Tier.GOLD,
        null,
        "Senna",
        null,
        BottomDuoStatFinder.SortKey.RANKING_DESC,
        0,
        6);

    verify(repository).findLatestPatchVersion();
    verify(repository).findTopRankingDesc("14.10", "GOLD", "Senna", null, 6);
  }
}
