package com.bestduo_BE.application.port;

import com.bestduo_BE.infra.persistence.entity.Summoner;

public interface SummonerRefreshStatusUpdater {

  Summoner findOrCreate(String puuid);

  void markRefreshRunning(String puuid);

  void markRefreshDone(String puuid, Long newLastMatchStartTime);

  void markRefreshError(String puuid);
}
