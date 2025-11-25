package com.bestduo_BE.infra.champion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bestduo_BE.application.port.ChampionMetaClient;
import com.bestduo_BE.domain.model.ChampionMeta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
@SpringBootTest
class ChampionMetaClientImplTest {

  @Autowired ChampionMetaClient championMetaClient;

  @Test
  @DisplayName("잘 찾나 확인")
  void findByIdReturnsChampion() {
    ChampionMeta meta = championMetaClient.findById("Lucian");

    assertEquals("Lucian", meta.getId());
    assertEquals("루시안", meta.getName());
    assertEquals(
        "https://ddragon.leagueoflegends.com/cdn/15.23.1/img/champion/Lucian.png",
        meta.getImageUrl());
  }

}
