package com.bestduo_BE.aggregate.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.bestduo_BE.aggregate.infra.persistence.BottomDuoMatchupFinder;
import com.bestduo_BE.common.application.port.ChampionMetaClient;
import com.bestduo_BE.common.domain.model.ChampionMeta;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.presentation.api.dto.BottomDuoCounterResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetBottomDuoCountersTest {

  @Mock
  private BottomDuoMatchupFinder matchupFinder;

  @Mock
  private ChampionMetaClient championMetaClient;

  private GetBottomDuoCounters useCase;

  @BeforeEach
  void setUp() {
    useCase = new GetBottomDuoCounters(matchupFinder, championMetaClient);
  }

  @Test
  @DisplayName("execute — 기본 카운터 사이즈를 사용하고 응답을 매핑한다")
  void executeUsesDefaultCounterSizeAndMapsResponse() {
    given(matchupFinder.resolvePatchVersion(null)).willReturn("14.10");
    given(matchupFinder.findMyDuoTotalGames(Tier.DIAMOND, "14.10", "ashe", "lux")).willReturn(80);
    BottomDuoMatchupFinder.MatchupRow row = new BottomDuoMatchupFinder.MatchupRow(
        "ashe", "lux", "draven", "pyke", Tier.DIAMOND, 30, 40
    );
    given(matchupFinder.findCountersByLowestWinRate(Tier.DIAMOND, "14.10", "ashe", "lux", 10))
        .willReturn(List.of(row));

    stubChampionMeta("ashe", "Ashe", "ashe.png");
    stubChampionMeta("lux", "Lux", "lux.png");
    stubChampionMeta("draven", "Draven", "draven.png");
    stubChampionMeta("pyke", "Pyke", "pyke.png");

    BottomDuoCounterResponse response = useCase.execute(Tier.DIAMOND, null, "ashe", "lux", null);

    assertEquals("DIAMOND", response.tier());
    assertEquals("14.10", response.patchVersion());
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
    then(matchupFinder).should().findCountersByLowestWinRate(Tier.DIAMOND, "14.10", "ashe", "lux", 10);
  }

  @Test
  @DisplayName("execute — 카운터 사이즈를 최소 1로 제한한다")
  void executeClampsCounterSizeToAtLeastOne() {
    given(matchupFinder.resolvePatchVersion(null)).willReturn("14.9");
    given(matchupFinder.findMyDuoTotalGames(Tier.GOLD, "14.9", "ashe", "lux")).willReturn(0);
    given(matchupFinder.findCountersByLowestWinRate(Tier.GOLD, "14.9", "ashe", "lux", 1))
        .willReturn(List.of());
    stubChampionMeta("ashe", "Ashe", "ashe.png");
    stubChampionMeta("lux", "Lux", "lux.png");

    BottomDuoCounterResponse response = useCase.execute(Tier.GOLD, null, "ashe", "lux", 0);

    assertEquals(1, response.counterSize());
    then(matchupFinder).should().findCountersByLowestWinRate(Tier.GOLD, "14.9", "ashe", "lux", 1);
  }

  @Test
  @DisplayName("execute — 카운터 사이즈를 최대 50으로 제한한다")
  void executeClampsCounterSizeToAtMostFifty() {
    given(matchupFinder.resolvePatchVersion(null)).willReturn("14.7");
    given(matchupFinder.findMyDuoTotalGames(Tier.PLATINUM, "14.7", "ashe", "lux")).willReturn(0);
    given(matchupFinder.findCountersByLowestWinRate(Tier.PLATINUM, "14.7", "ashe", "lux", 50))
        .willReturn(List.of());
    stubChampionMeta("ashe", "Ashe", "ashe.png");
    stubChampionMeta("lux", "Lux", "lux.png");

    BottomDuoCounterResponse response = useCase.execute(Tier.PLATINUM, null, "ashe", "lux", 100);

    assertEquals(50, response.counterSize());
    then(matchupFinder).should().findCountersByLowestWinRate(Tier.PLATINUM, "14.7", "ashe", "lux", 50);
  }

  private void stubChampionMeta(String id, String name, String imageUrl) {
    given(championMetaClient.findById(id)).willReturn(new ChampionMeta(id, name, imageUrl));
  }
}
