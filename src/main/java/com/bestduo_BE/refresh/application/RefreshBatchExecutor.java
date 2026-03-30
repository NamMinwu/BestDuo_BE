package com.bestduo_BE.refresh.application;

import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.persistence.repository.SummonerJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshBatchExecutor {

  private final SummonerJpaRepository summonerJpaRepository;
  private final RefreshSummonerMatches refreshSummonerMatches;

  public Result execute(int limit) {
    return execute(limit, Tier.ALL_TIERS);
  }

  public Result execute(int limit, Tier requestedTier) {
    var targets = summonerJpaRepository.findRefreshTargets(limit, requestedTier.name());

    int processed = 0;
    int success = 0;
    int failed = 0;
    int matchIdsEnqueued = 0;

    for (var s : targets) {
      processed++;
      try {
        var r = refreshSummonerMatches.execute(s.getPuuid(), requestedTier);
        success++;
        matchIdsEnqueued += r.matchIdsEnqueued(); // ✅ enqueue 수 집계
      } catch (Exception e) {
        failed++;
        // RefreshSummonerMatches 내부에서 markRefreshError까지 하니까 여기선 로그만
        log.warn("RefreshBatchExecutor failed. puuid={}", s.getPuuid(), e);
      }
    }

    return new Result(processed, success, failed, matchIdsEnqueued);
  }

  public record Result(
      int processed,
      int success,
      int failed,
      int matchIdsEnqueued
  ) {}
}
