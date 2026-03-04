package com.bestduo_BE.infra.persistence;

import com.bestduo_BE.application.port.MatchQueueCoordinator;
import com.bestduo_BE.domain.model.Tier;
import com.bestduo_BE.infra.persistence.entity.MatchQueue;
import com.bestduo_BE.infra.persistence.repository.MatchQueueJpaRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MatchQueueCoordinatorImpl implements MatchQueueCoordinator {

  private final MatchQueueJpaRepository repo;

  @Override
  @Transactional
  public int recoverStaleRunning(int staleMinutes) {
    return repo.recoverStaleRunning(staleMinutes);
  }

  @Override
  @Transactional
  public List<Item> pickAndLock(int limit, int maxRetry, int errorCooldownMinutes) {
    List<Item> out = new ArrayList<>();

    List<MatchQueue> ready = repo.pickReadyAndLock(limit);
    out.addAll(toItems(ready));

    int remaining = limit - ready.size();
    if (remaining > 0) {
      List<MatchQueue> errs = repo.pickRetryableErrorAndLock(remaining, maxRetry, errorCooldownMinutes);
      out.addAll(toItems(errs));
    }

    return out;
  }

  @Override
  @Transactional
  public void markDone(String matchId) {
    MatchQueue mq = repo.findById(matchId).orElseThrow();
    mq.markDone();
  }

  @Override
  @Transactional
  public void markError(String matchId, String message) {
    MatchQueue mq = repo.findById(matchId).orElseThrow();
    mq.markError(message);
  }

  @Override
  @Transactional
  public void unlockToReady(String matchId) {
    repo.unlockToReady(matchId);
  }

  private List<Item> toItems(List<MatchQueue> list) {
    List<Item> out = new ArrayList<>();
    for (MatchQueue mq : list) {
      Tier tier = Tier.valueOf(mq.getCollectionTier());
      out.add(new Item(mq.getMatchId(), tier, mq.getPriority()));
    }
    return out;
  }
}
