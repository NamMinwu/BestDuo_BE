package com.bestduo_BE.infra.persistence;

import com.bestduo_BE.application.port.MatchQueuePicker;
import com.bestduo_BE.domain.model.Tier;
import com.bestduo_BE.infra.persistence.entity.MatchQueue;
import com.bestduo_BE.infra.persistence.repository.MatchQueueJpaRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MatchQueuePickerImpl implements MatchQueuePicker {

  private final MatchQueueJpaRepository repo;

  @Override
  @Transactional(readOnly = true)
  public List<MatchQueueItem> pick(int limit) {
    return repo.pickReadyOrRetry(limit).stream()
        .map(mq -> new MatchQueueItem(mq.getMatchId(), Tier.valueOf(mq.getCollectionTier()), mq.getPriority()))
        .toList();
  }

  @Override
  @Transactional
  public void markRunning(String matchId) {
    MatchQueue mq = repo.findById(matchId)
        .orElseThrow(() -> new IllegalStateException("match_queue not found: " + matchId));
    mq.markRunning();
  }

  @Override
  @Transactional
  public void markDone(String matchId) {
    MatchQueue mq = repo.findById(matchId)
        .orElseThrow(() -> new IllegalStateException("match_queue not found: " + matchId));
    mq.markDone();
  }

  @Override
  @Transactional
  public void markError(String matchId, String message) {
    MatchQueue mq = repo.findById(matchId)
        .orElseThrow(() -> new IllegalStateException("match_queue not found: " + matchId));
    mq.markError(message);
  }
}
