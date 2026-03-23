package com.bestduo_BE.refresh.application.port;

import com.bestduo_BE.common.infra.persistence.entity.Summoner;

public interface SummonerRefreshStatusUpdater {

  Summoner findOrCreate(String puuid);

  void syncRefreshCursor(String puuid, Long newLastMatchStartTime);
}
