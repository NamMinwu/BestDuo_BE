package com.bestduo_BE.ingest.application.port;

import com.bestduo_BE.common.domain.model.Tier;
import java.util.List;

public interface MatchQueuePicker {
  List<MatchQueueItem> pick(int limit);
  void markRunning(String matchId);
  void markDone(String matchId);
  void markError(String matchId, String message);

  record MatchQueueItem(String matchId, Tier tier, int priority) {}
}
