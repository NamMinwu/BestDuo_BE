package com.bestduo_BE.common.infra.champion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bestduo_BE.aggregate.application.port.ChampionMetaClient;
import com.bestduo_BE.common.domain.model.ChampionMeta;
import com.bestduo_BE.common.infra.champion.ChampionMetaClientImpl;
import com.bestduo_BE.common.infra.champion.DataDragonChampionDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChampionMetaClientImplTest {

  private static ChampionMetaClient championMetaClient;

  @BeforeAll
  static void setUpClient() {
    DataDragonChampionDataSource dataSource = new DataDragonChampionDataSource();
    dataSource.loadChampions();
    championMetaClient = new ChampionMetaClientImpl(dataSource);
  }

  @Test
  @DisplayName("챔피언 아이디와 키 모두로 메타 정보를 조회한다")
  void findByIdReturnsChampionByNameAndKey() {
    ChampionMeta byName = championMetaClient.findById("236");

    assertEquals("Lucian", byName.id());
    assertEquals("루시안", byName.name());
    assertEquals(
        "https://ddragon.leagueoflegends.com/cdn/15.23.1/img/champion/Lucian.png",
        byName.imageUrl());
  }
}
