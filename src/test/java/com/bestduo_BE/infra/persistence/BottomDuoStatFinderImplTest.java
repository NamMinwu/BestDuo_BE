package com.bestduo_BE.infra.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bestduo_BE.application.port.BottomDuoStatFinder;
import com.bestduo_BE.domain.model.Tier;
import com.bestduo_BE.infra.persistence.entity.BottomDuoStatAgg;
import com.bestduo_BE.infra.persistence.repository.BottomDuoStatAggJpaRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BottomDuoStatFinderImplTest {

  @Mock
  private BottomDuoStatAggJpaRepository repository;

  private BottomDuoStatFinderImpl finder;

  @BeforeEach
  void setUp() {
    finder = new BottomDuoStatFinderImpl(repository);
  }

  @Test
  void findTierTotalGamesDelegatesToRepository() {
    when(repository.sumGamesByTier("GOLD")).thenReturn(128);

    int games = finder.findTierTotalGames(Tier.GOLD);

    assertThat(games).isEqualTo(128);
    verify(repository).sumGamesByTier("GOLD");
  }

  @Test
  void findStatsMapsEntitiesForWinRateDesc() {
    OffsetDateTime now = OffsetDateTime.now();
    BottomDuoStatAgg entity = BottomDuoStatAgg.builder()
        .adcChampionId("Ashe")
        .supChampionId("Lux")
        .tier(Tier.EMERALD.name())
        .wins(30)
        .games(50)
        .createdAt(now)
        .updatedAt(now)
        .build();

    when(repository.findTopWinRateDesc("EMERALD", "Ashe", "Lux", 3))
        .thenReturn(List.of(entity));

    List<BottomDuoStatFinder.StatRow> rows = finder.findStats(
        Tier.EMERALD,
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
    verify(repository).findTopWinRateDesc("EMERALD", "Ashe", "Lux", 3);
  }

  @Test
  void findStatsTrimsInputsAndEnforcesMinimumLimitForWinRateAsc() {
    when(repository.findTopWinRateAsc("SILVER", "Caitlyn", null, 1))
        .thenReturn(List.of());

    finder.findStats(
        Tier.SILVER,
        "  Caitlyn  ",
        "   ",
        BottomDuoStatFinder.SortKey.WINRATE_ASC,
        0,
        0);

    ArgumentCaptor<String> adcCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> supCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Integer> limitCaptor = ArgumentCaptor.forClass(Integer.class);

    verify(repository).findTopWinRateAsc(
        eq("SILVER"),
        adcCaptor.capture(),
        supCaptor.capture(),
        limitCaptor.capture());

    assertThat(adcCaptor.getValue()).isEqualTo("Caitlyn");
    assertThat(supCaptor.getValue()).isNull();
    assertThat(limitCaptor.getValue()).isEqualTo(1);
  }

  @Test
  void findStatsPassesTierTotalGamesForPickRateDesc() {
    when(repository.findTopPickRateDesc("DIAMOND", null, null, 777, 5))
        .thenReturn(List.of());

    finder.findStats(
        Tier.DIAMOND,
        null,
        null,
        BottomDuoStatFinder.SortKey.PICKRATE_DESC,
        777,
        5);

    verify(repository).findTopPickRateDesc("DIAMOND", null, null, 777, 5);
  }

  @Test
  void findStatsUsesPickRateAscQuery() {
    when(repository.findTopPickRateAsc("BRONZE", null, "Thresh", 321, 2))
        .thenReturn(List.of());

    finder.findStats(
        Tier.BRONZE,
        null,
        "Thresh",
        BottomDuoStatFinder.SortKey.PICKRATE_ASC,
        321,
        2);

    verify(repository).findTopPickRateAsc("BRONZE", null, "Thresh", 321, 2);
  }
}
