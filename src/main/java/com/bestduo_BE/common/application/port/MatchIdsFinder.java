package com.bestduo_BE.common.application.port;

import java.util.List;

public interface MatchIdsFinder {
  List<String> findRecentMatchIds(String puuid, int count);

  // Riot API startTime 파라미터는 "epoch seconds"
  List<String> findMatchIdsSince(String puuid, long startTimeSeconds, int count);

  // grace period 중 사용: startTime ~ endTime 범위로 제한
  List<String> findMatchIdsBetween(String puuid, long startTimeSeconds, long endTimeSeconds, int count);
}
