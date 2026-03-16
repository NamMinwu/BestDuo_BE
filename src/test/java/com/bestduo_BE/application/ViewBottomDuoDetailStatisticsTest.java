package com.bestduo_BE.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.bestduo_BE.application.port.BottomDuoMatchupFinder;
import com.bestduo_BE.application.port.ChampionMetaClient;
import com.bestduo_BE.domain.model.ChampionMeta;
import com.bestduo_BE.domain.model.Tier;
import com.bestduo_BE.presentation.api.dto.BottomDuoDetailStatisticsResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ViewBottomDuoDetailStatisticsTest {

  @Mock
  private BottomDuoMatchupFinder matchupFinder;

  @Mock
  private ChampionMetaClient championMetaClient;

  private ViewBottomDuoDetailStatistics useCase;

  @BeforeEach
  void setUp() {
    useCase = new ViewBottomDuoDetailStatistics(matchupFinder, championMetaClient);
  }

  @Test
  void executeBuildsResponseWithPickRates() {
    given(matchupFinder.findMyDuoTotalGames(Tier.EMERALD, "ashe", "lux")).willReturn(120);
    BottomDuoMatchupFinder.MatchupRow row = new BottomDuoMatchupFinder.MatchupRow(
        "ashe", "lux", "jinx", "morgana", Tier.EMERALD, 60, 40
    );
    given(matchupFinder.findMatchups(
        Tier.EMERALD,
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
        "ashe",
        "lux",
        "jinx",
        "morgana",
        BottomDuoMatchupFinder.SortKey.WINRATE_DESC
    );

    assertEquals("EMERALD", response.tier());
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
  void executeNormalizesOpponentFiltersAndCapsRows() {
    given(matchupFinder.findMyDuoTotalGames(Tier.GOLD, "ashe", "lux")).willReturn(0);
    given(matchupFinder.findMatchups(
        Tier.GOLD,
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
        "ashe",
        "lux",
        "  ",
        "\t",
        BottomDuoMatchupFinder.SortKey.PICKRATE_ASC
    );

    then(matchupFinder).should().findMatchups(
        eq(Tier.GOLD),
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
