package com.bestduo_BE.common.application.port;

import java.util.List;

public interface SummonerRefreshCursorTracker {

  boolean hasPendingMatches(String puuid);

  Long registerRefreshBatch(String puuid, List<String> orderedMatchIds, Long currentCursor);

  void confirmMatchIngested(String matchId, Long matchStartTimeSec);
}
