package com.bestduo_BE.ingest.infra.persistence;

import com.bestduo_BE.ingest.application.port.MatchQueueDispatcher;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.persistence.entity.MatchQueue;
import com.bestduo_BE.common.infra.persistence.repository.MatchQueueJpaRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MatchQueueDispatcherImpl implements MatchQueueDispatcher {

  private final MatchQueueJpaRepository repo;

  @Override
  @Transactional
  public int recoverStaleRunning(int staleMinutes) {
    return repo.recoverStaleRunning(staleMinutes);
  }

  @Override
  @Transactional
  public List<Item> pickAndLock(int limit, int maxRetry, int errorCooldownMinutes) {
    return pickAndLock(limit, maxRetry, errorCooldownMinutes, Tier.ALL_TIERS);
  }

  @Override
  @Transactional
  public List<Item> pickAndLock(int limit, int maxRetry, int errorCooldownMinutes, Tier requestedTier) {
    List<Item> out = new ArrayList<>();
    String collectionTier = toCollectionTier(requestedTier);

    List<MatchQueue> ready = repo.pickReadyAndLock(limit, collectionTier);
    out.addAll(toItems(ready));

    int remaining = limit - ready.size();
    if (remaining > 0) {
      List<MatchQueue> errs = repo.pickRetryableErrorAndLock(remaining, maxRetry, errorCooldownMinutes, collectionTier);
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

  @Override
  @Transactional
  public List<Item> pickAndLockWithPriority(int limit, int maxRetry, int errorCooldownMinutes, String currentPatch) {
    List<Item> out = new ArrayList<>();

    List<MatchQueue> ready = repo.pickReadyWithPriorityAndLock(limit, null, currentPatch);
    out.addAll(toItems(ready));

    int remaining = limit - ready.size();
    if (remaining > 0) {
      List<MatchQueue> errs = repo.pickRetryableErrorAndLock(remaining, maxRetry, errorCooldownMinutes, null);
      out.addAll(toItems(errs));
    }

    return out;
  }

  private List<Item> toItems(List<MatchQueue> list) {
    List<Item> out = new ArrayList<>();
    for (MatchQueue mq : list) {
      Tier tier = Tier.valueOf(mq.getCollectionTier());
      out.add(new Item(mq.getMatchId(), tier, mq.getPriority(), mq.getPatch()));
    }
    return out;
  }

  private String toCollectionTier(Tier requestedTier) {
    if (requestedTier == null || requestedTier == Tier.ALL_TIERS) {
      return null;
    }
    return requestedTier.name();
  }
}
