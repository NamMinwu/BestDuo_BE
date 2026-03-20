package com.bestduo_BE.aggregate.infra.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.bestduo_BE.aggregate.application.port.BottomDuoMatchupFinder;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.aggregate.infra.persistence.entity.BottomDuoMatchupAgg;
import com.bestduo_BE.aggregate.infra.persistence.repository.BottomDuoMatchupAggJpaRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BottomDuoMatchupFinderImplTest {

  @Mock
  private BottomDuoMatchupAggJpaRepository repository;

  private BottomDuoMatchupFinderImpl finder;

  @BeforeEach
  void setUp() {
    finder = new BottomDuoMatchupFinderImpl(repository);
  }

  @Test
  void findMyDuoTotalGamesDelegatesToRepository() {
    given(repository.sumGamesOfMyDuo("14.9", "DIAMOND", "ashe", "lux")).willReturn(87);

    int totalGames = finder.findMyDuoTotalGames(Tier.DIAMOND, "14.9", "ashe", "lux");

    assertEquals(87, totalGames);
  }

  @Test
  void resolvePatchVersionPrefersProvidedValue() {
    String patch = finder.resolvePatchVersion(" 14.8 ");

    assertEquals("14.8", patch);
  }

  @Test
  void resolvePatchVersionFallsBackToRepository() {
    given(repository.findLatestPatchVersion()).willReturn("14.10");

    String patch = finder.resolvePatchVersion(null);

    assertEquals("14.10", patch);
    then(repository).should().findLatestPatchVersion();
  }

  @Test
  void findMatchupsWinRateDescMapsEntitiesAndTrimsOpponentFilters() {
    OffsetDateTime now = OffsetDateTime.now();
    BottomDuoMatchupAgg entity = BottomDuoMatchupAgg.builder()
        .patchVersion("14.10")
        .myAdcChampionId("ashe")
        .mySupChampionId("lux")
        .oppAdcChampionId("jinx")
        .oppSupChampionId("morgana")
        .tier("DIAMOND")
        .wins(30)
        .games(50)
        .createdAt(now)
        .updatedAt(now)
        .build();
    given(repository.findTopWinRateDesc(eq("14.10"), eq("DIAMOND"), eq("ashe"), eq("lux"), isNull(), isNull(), eq(5)))
        .willReturn(List.of(entity));

    List<BottomDuoMatchupFinder.MatchupRow> rows = finder.findMatchups(
        Tier.DIAMOND,
        "14.10",
        "ashe",
        "lux",
        "   ",
        "\t",
        BottomDuoMatchupFinder.SortKey.WINRATE_DESC,
        100,
        5
    );

    assertEquals(1, rows.size());
    BottomDuoMatchupFinder.MatchupRow row = rows.get(0);
    assertEquals("ashe", row.myAdcChampionId());
    assertEquals("lux", row.mySupChampionId());
    assertEquals("jinx", row.oppAdcChampionId());
    assertEquals("morgana", row.oppSupChampionId());
    assertEquals(Tier.DIAMOND, row.tier());
    assertEquals(30, row.wins());
    assertEquals(50, row.games());
  }

  @Test
  void findMatchupsWinRateAscCallsRepository() {
    given(repository.findTopWinRateAsc("14.10", "DIAMOND", "ashe", "lux", "jinx", "morgana", 3))
        .willReturn(List.of());

    finder.findMatchups(
        Tier.DIAMOND,
        "14.10",
        "ashe",
        "lux",
        "jinx",
        "morgana",
        BottomDuoMatchupFinder.SortKey.WINRATE_ASC,
        0,
        3
    );

    then(repository).should().findTopWinRateAsc("14.10", "DIAMOND", "ashe", "lux", "jinx", "morgana", 3);
  }

  @Test
  void findMatchupsPickRateDescEnforcesMinimumLimit() {
    given(repository.findTopPickRateDesc("14.10", "DIAMOND", "ashe", "lux", "jinx", null, 200, 1))
        .willReturn(List.of());

    finder.findMatchups(
        Tier.DIAMOND,
        "14.10",
        "ashe",
        "lux",
        "jinx",
        null,
        BottomDuoMatchupFinder.SortKey.PICKRATE_DESC,
        200,
        0
    );

    then(repository).should().findTopPickRateDesc("14.10", "DIAMOND", "ashe", "lux", "jinx", null, 200, 1);
  }

  @Test
  void findMatchupsPickRateAscPassesThroughMyTotalGames() {
    given(repository.findTopPickRateAsc("14.10", "DIAMOND", "ashe", "lux", null, null, 321, 4))
        .willReturn(List.of());

    finder.findMatchups(
        Tier.DIAMOND,
        "14.10",
        "ashe",
        "lux",
        null,
        null,
        BottomDuoMatchupFinder.SortKey.PICKRATE_ASC,
        321,
        4
    );

    then(repository).should().findTopPickRateAsc("14.10", "DIAMOND", "ashe", "lux", null, null, 321, 4);
  }

  @Test
  void findCountersByLowestWinRateMapsEntitiesAndAppliesLimitFloor() {
    OffsetDateTime now = OffsetDateTime.now();
    BottomDuoMatchupAgg entity = BottomDuoMatchupAgg.builder()
        .patchVersion("14.10")
        .myAdcChampionId("xayah")
        .mySupChampionId("rakan")
        .oppAdcChampionId("draven")
        .oppSupChampionId("pyke")
        .tier("GOLD")
        .wins(10)
        .games(40)
        .createdAt(now)
        .updatedAt(now)
        .build();
    given(repository.findCountersByLowestWinRate("14.10", "GOLD", "xayah", "rakan", 1))
        .willReturn(List.of(entity));

    List<BottomDuoMatchupFinder.MatchupRow> rows = finder.findCountersByLowestWinRate(
        Tier.GOLD,
        "14.10",
        "xayah",
        "rakan",
        0
    );

    then(repository).should().findCountersByLowestWinRate("14.10", "GOLD", "xayah", "rakan", 1);
    assertEquals(1, rows.size());
    BottomDuoMatchupFinder.MatchupRow row = rows.get(0);
    assertEquals("xayah", row.myAdcChampionId());
    assertEquals("rakan", row.mySupChampionId());
    assertEquals("draven", row.oppAdcChampionId());
    assertEquals("pyke", row.oppSupChampionId());
    assertEquals(Tier.GOLD, row.tier());
    assertEquals(10, row.wins());
    assertEquals(40, row.games());
    assertTrue(row.winRate() < 1);
  }
}
