package com.bestduo_BE.application.port;

public interface SummonerSeedRegistry {
  boolean registerIfAbsent(String puuid);
  void markRunning(String puuid);
  void markDone(String puuid);
  void markError(String puuid);
}
