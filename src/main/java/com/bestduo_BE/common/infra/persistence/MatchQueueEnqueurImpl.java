package com.bestduo_BE.common.infra.persistence;

import com.bestduo_BE.common.application.port.MatchQueueEnqueuer;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.persistence.entity.MatchQueue;
import com.bestduo_BE.common.infra.persistence.repository.MatchQueueJpaRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MatchQueueEnqueurImpl implements MatchQueueEnqueuer {
  private final MatchQueueJpaRepository repo;

  @Override
  @Transactional
  public void enqueueAllIdempotent(List<String> matchIds, Tier tier, int priority) {
    for (String matchId : matchIds) {
      if (repo.existsById(matchId)) continue; // ✅ 멱등
      repo.save(MatchQueue.newReady(matchId, tier.name(), priority));
    }
  }

}
