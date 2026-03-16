package com.bestduo_BE.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.bestduo_BE.application.port.BottomDuoMatchupFinder;
import com.bestduo_BE.application.port.ChampionMetaClient;
import com.bestduo_BE.domain.model.ChampionMeta;
import com.bestduo_BE.domain.model.Tier;
import com.bestduo_BE.presentation.api.dto.BottomDuoCounterResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ViewBottomDuoCountersTest {

  @Mock
  private BottomDuoMatchupFinder matchupFinder;

  @Mock
  private ChampionMetaClient championMetaClient;

  private ViewBottomDuoCounters useCase;

  @BeforeEach
  void setUp() {
    useCase = new ViewBottomDuoCounters(matchupFinder, championMetaClient);
  }

  @Test
  void executeUsesDefaultCounterSizeAndMapsResponse() {
    given(matchupFinder.findMyDuoTotalGames(Tier.DIAMOND, "ashe", "lux")).willReturn(80);
    BottomDuoMatchupFinder.MatchupRow row = new BottomDuoMatchupFinder.MatchupRow(
        "ashe", "lux", "draven", "pyke", Tier.DIAMOND, 30, 40
    );
    given(matchupFinder.findCountersByLowestWinRate(Tier.DIAMOND, "ashe", "lux", 10))
        .willReturn(List.of(row));

    stubChampionMeta("ashe", "Ashe", "ashe.png");
    stubChampionMeta("lux", "Lux", "lux.png");
    stubChampionMeta("draven", "Draven", "draven.png");
    stubChampionMeta("pyke", "Pyke", "pyke.png");

    BottomDuoCounterResponse response = useCase.execute(Tier.DIAMOND, "ashe", "lux", null);

    assertEquals("DIAMOND", response.tier());
    assertEquals(80, response.totalGames());
    assertEquals(10, response.counterSize());
    assertEquals("ashe", response.myDuo().adcId());
    assertEquals("Ashe", response.myDuo().adcName());
    assertEquals("lux", response.myDuo().supId());
    assertEquals("Lux", response.myDuo().supName());
    assertEquals(1, response.counters().size());
    BottomDuoCounterResponse.Item item = response.counters().get(0);
    assertEquals("draven", item.oppAdcId());
    assertEquals("Draven", item.oppAdcName());
    assertEquals("pyke", item.oppSupId());
    assertEquals("Pyke", item.oppSupName());
    assertEquals(row.winRate(), item.winRate(), 1e-9);
    assertEquals(0.5, item.pickRate(), 1e-9);
    assertEquals(40, item.games());
    then(matchupFinder).should().findCountersByLowestWinRate(Tier.DIAMOND, "ashe", "lux", 10);
  }

  @Test
  void executeClampsCounterSizeToAtLeastOne() {
    given(matchupFinder.findMyDuoTotalGames(Tier.GOLD, "ashe", "lux")).willReturn(0);
    given(matchupFinder.findCountersByLowestWinRate(Tier.GOLD, "ashe", "lux", 1))
        .willReturn(List.of());
    stubChampionMeta("ashe", "Ashe", "ashe.png");
    stubChampionMeta("lux", "Lux", "lux.png");

    BottomDuoCounterResponse response = useCase.execute(Tier.GOLD, "ashe", "lux", 0);

    assertEquals(1, response.counterSize());
    then(matchupFinder).should().findCountersByLowestWinRate(Tier.GOLD, "ashe", "lux", 1);
  }

  @Test
  void executeClampsCounterSizeToAtMostFifty() {
    given(matchupFinder.findMyDuoTotalGames(Tier.PLATINUM, "ashe", "lux")).willReturn(0);
    given(matchupFinder.findCountersByLowestWinRate(Tier.PLATINUM, "ashe", "lux", 50))
        .willReturn(List.of());
    stubChampionMeta("ashe", "Ashe", "ashe.png");
    stubChampionMeta("lux", "Lux", "lux.png");

    BottomDuoCounterResponse response = useCase.execute(Tier.PLATINUM, "ashe", "lux", 100);

    assertEquals(50, response.counterSize());
    then(matchupFinder).should().findCountersByLowestWinRate(Tier.PLATINUM, "ashe", "lux", 50);
  }

  private void stubChampionMeta(String id, String name, String imageUrl) {
    given(championMetaClient.findById(id)).willReturn(new ChampionMeta(id, name, imageUrl));
  }
}
