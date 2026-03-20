package com.bestduo_BE.common.infra.champion;

import com.bestduo_BE.common.domain.model.ChampionMeta;

public interface ChampionDataSource {
  ChampionMeta getById(String championId);
}
