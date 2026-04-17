package com.bestduo_BE.common.infra.persistence;

import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.persistence.entity.MatchQueue;
import com.bestduo_BE.common.infra.persistence.repository.MatchQueueJpaRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MatchQueueEnqueuer {
  private final MatchQueueJpaRepository repo;

  @Transactional
  public void enqueueAllIdempotent(List<String> matchIds, Tier tier, int priority, String patch) {
    for (String matchId : matchIds) {
      if (repo.existsById(matchId)) continue;
      repo.save(MatchQueue.newReady(matchId, tier, priority, patch));
    }
  }
}
