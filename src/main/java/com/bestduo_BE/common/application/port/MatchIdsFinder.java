package com.bestduo_BE.common.application.port;

import java.util.List;

public interface MatchIdsFinder {
  List<String> findRecentMatchIds(String puuid, int count);
  // ✅ 신규(Phase5 증분 refresh)
  // Riot API startTime 파라미터는 "epoch seconds"
  List<String> findMatchIdsSince(String puuid, long startTimeSeconds, int count);
}
