package com.bestduo_BE.common.application.port;

import com.bestduo_BE.common.domain.model.Tier;
import java.util.List;

public interface MatchQueueEnqueuer {
  void enqueueAllIdempotent(List<String> matchIds, Tier tier, int priority, String patch);
}
