package com.bestduo_BE.seed.application.port;

public interface SummonerSeedRegistry {
  boolean registerIfAbsent(String puuid);
}
