package com.bestduo_BE.infra.champion;

import com.bestduo_BE.application.port.ChampionMetaClient;
import com.bestduo_BE.domain.model.ChampionMeta;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChampionMetaClientImpl implements ChampionMetaClient {

  private final ChampionDataSource dataSource;

  @Override
  public ChampionMeta findById(String championId) {
    ChampionMeta meta = dataSource.getById(championId);
    if (meta == null) {
      throw new IllegalArgumentException("Champion not found: " + championId);
    }
    return meta;
  }
}
