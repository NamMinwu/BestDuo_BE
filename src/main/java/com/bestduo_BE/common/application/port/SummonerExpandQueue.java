package com.bestduo_BE.common.application.port;

import java.util.List;

public interface SummonerExpandQueue {
  List<String> findReadyPuuds(int limit);
  void markExpandRunning(String puuid);
  void markExpandDone(String puuid);
  void markExpandError(String puuid);

  boolean registerIfAbsent(String puuid); // participants로 들어온 seed 등록용
}
