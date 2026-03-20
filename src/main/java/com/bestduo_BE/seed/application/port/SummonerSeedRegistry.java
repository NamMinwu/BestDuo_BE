package com.bestduo_BE.seed.application.port;

public interface SummonerSeedRegistry {
  boolean registerIfAbsent(String puuid);
  void markSeedRunning(String puuid);
  void markSeedDone(String puuid);
  void markSeedError(String puuid);
}
