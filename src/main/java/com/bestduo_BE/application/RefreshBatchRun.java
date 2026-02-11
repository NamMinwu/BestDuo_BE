package com.bestduo_BE.application;

import com.bestduo_BE.infra.persistence.repository.SummonerJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshBatchRun {

  private final SummonerJpaRepository summonerJpaRepository;
  private final RefreshSummonerMatches refreshSummonerMatches;

  public Result execute(int limit) {
    var targets = summonerJpaRepository.findRefreshTargets(limit);

    int processed = 0;
    int success = 0;
    int failed = 0;
    int matchIdsEnqueued = 0;

    for (var s : targets) {
      processed++;
      try {
        var r = refreshSummonerMatches.execute(s.getPuuid());
        success++;
        matchIdsEnqueued += r.matchIdsEnqueued(); // ✅ enqueue 수 집계
      } catch (Exception e) {
        failed++;
        // RefreshSummonerMatches 내부에서 markRefreshError까지 하니까 여기선 로그만
        log.warn("RefreshBatchRun failed. puuid={}", s.getPuuid(), e);
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
