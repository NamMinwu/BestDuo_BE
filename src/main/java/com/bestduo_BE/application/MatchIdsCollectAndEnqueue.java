package com.bestduo_BE.application;

import com.bestduo_BE.application.port.MatchIdsFinder;
import com.bestduo_BE.application.port.MatchQueueEnqueuer;
import com.bestduo_BE.domain.model.Tier;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MatchIdsCollectAndEnqueue {

  private final MatchIdsFinder matchIdsFinder;
  private final MatchQueueEnqueuer matchQueueEnqueuer;

  public int enqueueRecent(String puuid, Tier tier, int count, int priority) {
    List<String> ids = matchIdsFinder.findRecentMatchIds(puuid, count);
    matchQueueEnqueuer.enqueueAllIdempotent(ids, tier, priority);
    return ids.size();
  }

  public int enqueueSince(String puuid, Long sinceStartTimeSec, Tier tier, int count, int priority) {
    List<String> ids = matchIdsFinder.findMatchIdsSince(puuid, sinceStartTimeSec, count);
    matchQueueEnqueuer.enqueueAllIdempotent(ids, tier, priority);
    return ids.size();
  }
}
