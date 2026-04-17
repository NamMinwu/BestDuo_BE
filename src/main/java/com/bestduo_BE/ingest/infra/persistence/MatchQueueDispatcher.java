package com.bestduo_BE.ingest.infra.persistence;

import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.persistence.entity.MatchQueue;
import com.bestduo_BE.common.infra.persistence.repository.MatchQueueJpaRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MatchQueueDispatcher {

  private final MatchQueueJpaRepository repo;

  @Transactional
  public int recoverStaleRunning(int staleMinutes) {
    return repo.recoverStaleRunning(staleMinutes);
  }

  @Transactional
  public List<Item> pickAndLock(int limit, int maxRetry, int errorCooldownMinutes) {
    return pickAndLock(limit, maxRetry, errorCooldownMinutes, null);
  }

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

  @Transactional
  public void markDone(String matchId) {
    MatchQueue mq = repo.findById(matchId).orElseThrow();
    mq.markDone();
  }

  @Transactional
  public void markError(String matchId, String message) {
    MatchQueue mq = repo.findById(matchId).orElseThrow();
    mq.markError(message);
  }

  @Transactional
  public void unlockToReady(String matchId) {
    repo.unlockToReady(matchId);
  }

  @Transactional
  public List<Item> pickAndLockWithPriority(int limit, int maxRetry, int errorCooldownMinutes,
      Tier requestedTier, String currentPatch) {
    List<Item> out = new ArrayList<>();
    String collectionTier = toCollectionTier(requestedTier);

    List<MatchQueue> ready = repo.pickReadyWithPriorityAndLock(limit, collectionTier, currentPatch);
    out.addAll(toItems(ready));

    int remaining = limit - ready.size();
    if (remaining > 0) {
      List<MatchQueue> errs = repo.pickRetryableErrorAndLock(remaining, maxRetry, errorCooldownMinutes, collectionTier);
      out.addAll(toItems(errs));
    }

    return out;
  }

  private List<Item> toItems(List<MatchQueue> list) {
    List<Item> out = new ArrayList<>();
    for (MatchQueue mq : list) {
      out.add(new Item(mq.getMatchId(), mq.getCollectionTier(), mq.getPriority(), mq.getPatch()));
    }
    return out;
  }

  private String toCollectionTier(Tier requestedTier) {
    if (requestedTier == null) {
      return null;
    }
    return requestedTier.name();
  }

  public record Item(String matchId, Tier tier, int priority, String patch) {}
}
