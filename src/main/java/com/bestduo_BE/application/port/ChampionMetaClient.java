package com.bestduo_BE.application.port;

import com.bestduo_BE.domain.model.ChampionMeta;

public interface ChampionMetaClient {
  ChampionMeta findById(String championId);
}

