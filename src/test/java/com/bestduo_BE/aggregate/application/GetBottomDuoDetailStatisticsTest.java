package com.bestduo_BE.aggregate.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.bestduo_BE.aggregate.application.port.BottomDuoMatchupFinder;
import com.bestduo_BE.aggregate.application.port.ChampionMetaClient;
import com.bestduo_BE.common.domain.model.ChampionMeta;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.presentation.api.dto.BottomDuoDetailStatisticsResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetBottomDuoDetailStatisticsTest {

  @Mock
  private BottomDuoMatchupFinder matchupFinder;

  @Mock
  private ChampionMetaClient championMetaClient;

  private GetBottomDuoDetailStatistics useCase;

  @BeforeEach
  void setUp() {
    useCase = new GetBottomDuoDetailStatistics(matchupFinder, championMetaClient);
  }

  @Test
  @DisplayName("execute — 픽률을 포함한 상세 통계 응답을 조립한다")
  void executeBuildsResponseWithPickRates() {
    given(matchupFinder.resolvePatchVersion(null)).willReturn("14.10");
    given(matchupFinder.findMyDuoTotalGames(Tier.EMERALD, "14.10", "ashe", "lux")).willReturn(120);
    BottomDuoMatchupFinder.MatchupRow row = new BottomDuoMatchupFinder.MatchupRow(
        "ashe", "lux", "jinx", "morgana", Tier.EMERALD, 60, 40
    );
    given(matchupFinder.findMatchups(
        Tier.EMERALD,
        "14.10",
        "ashe",
        "lux",
        "jinx",
        "morgana",
        BottomDuoMatchupFinder.SortKey.WINRATE_DESC,
        120,
        100
    )).willReturn(List.of(row));

    stubChampionMeta("ashe", "Ashe", "ashe.png");
    stubChampionMeta("lux", "Lux", "lux.png");
    stubChampionMeta("jinx", "Jinx", "jinx.png");
    stubChampionMeta("morgana", "Morgana", "morgana.png");

    BottomDuoDetailStatisticsResponse response = useCase.execute(
        Tier.EMERALD,
        null,
        "ashe",
        "lux",
        "jinx",
        "morgana",
        BottomDuoMatchupFinder.SortKey.WINRATE_DESC
    );

    assertEquals("EMERALD", response.tier());
    assertEquals("14.10", response.patchVersion());
    assertEquals(120, response.totalGames());
    assertEquals("ashe", response.myDuo().adcId());
    assertEquals("Ashe", response.myDuo().adcName());
    assertEquals("lux", response.myDuo().supId());
    assertEquals(1, response.items().size());
    BottomDuoDetailStatisticsResponse.Item item = response.items().get(0);
    assertEquals("jinx", item.oppAdcId());
    assertEquals("Jinx", item.oppAdcName());
    assertEquals("morgana", item.oppSupId());
    assertEquals("Morgana", item.oppSupName());
    assertEquals(row.winRate(), item.winRate(), 1e-9);
    assertEquals(40.0 / 120.0, item.pickRate(), 1e-9);
    assertEquals(40, item.games());
  }

  @Test
  @DisplayName("execute — 상대 필터를 정규화하고 최대 행 수를 제한한다")
  void executeNormalizesOpponentFiltersAndCapsRows() {
    given(matchupFinder.resolvePatchVersion(null)).willReturn("14.8");
    given(matchupFinder.findMyDuoTotalGames(Tier.GOLD, "14.8", "ashe", "lux")).willReturn(0);
    given(matchupFinder.findMatchups(
        Tier.GOLD,
        "14.8",
        "ashe",
        "lux",
        null,
        null,
        BottomDuoMatchupFinder.SortKey.PICKRATE_ASC,
        0,
        100
    )).willReturn(List.of());
    stubChampionMeta("ashe", "Ashe", "ashe.png");
    stubChampionMeta("lux", "Lux", "lux.png");

    useCase.execute(
        Tier.GOLD,
        null,
        "ashe",
        "lux",
        "  ",
        "\t",
        BottomDuoMatchupFinder.SortKey.PICKRATE_ASC
    );

    then(matchupFinder).should().findMatchups(
        eq(Tier.GOLD),
        eq("14.8"),
        eq("ashe"),
        eq("lux"),
        isNull(),
        isNull(),
        eq(BottomDuoMatchupFinder.SortKey.PICKRATE_ASC),
        eq(0),
        eq(100)
    );
  }

  private void stubChampionMeta(String id, String name, String imageUrl) {
    given(championMetaClient.findById(id)).willReturn(new ChampionMeta(id, name, imageUrl));
  }
}
