package com.bestduo_BE.infra.champion;

import com.bestduo_BE.domain.model.ChampionMeta;

public interface ChampionDataSource {
  ChampionMeta getById(String championId);
}
