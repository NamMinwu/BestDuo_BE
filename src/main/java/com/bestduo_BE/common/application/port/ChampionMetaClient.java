package com.bestduo_BE.common.application.port;

import com.bestduo_BE.common.domain.model.ChampionMeta;

public interface ChampionMetaClient {
  ChampionMeta findById(String championId);
}
