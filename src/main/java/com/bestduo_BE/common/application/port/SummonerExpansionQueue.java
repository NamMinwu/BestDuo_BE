package com.bestduo_BE.common.application.port;

public interface SummonerExpansionQueue {
  boolean registerIfAbsent(String puuid); // participants로 들어온 seed 등록용
}
