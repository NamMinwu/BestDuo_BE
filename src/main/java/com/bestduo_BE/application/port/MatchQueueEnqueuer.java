package com.bestduo_BE.application.port;

import com.bestduo_BE.domain.model.Tier;
import java.util.List;

public interface MatchQueueEnqueuer {
  void enqueueAllIdempotent(List<String> matchIds, Tier tier, int priority);
}
