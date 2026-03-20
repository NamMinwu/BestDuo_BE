package com.bestduo_BE.common.infra.champion;

import com.bestduo_BE.aggregate.application.port.ChampionMetaClient;
import com.bestduo_BE.common.domain.model.ChampionMeta;
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
