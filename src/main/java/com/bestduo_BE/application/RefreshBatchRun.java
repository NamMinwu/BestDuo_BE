package com.bestduo_BE.application;

import com.bestduo_BE.infra.persistence.repository.SummonerJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RefreshBatchRun {

  private final SummonerJpaRepository summonerJpaRepository;
  private final RefreshSummonerMatches refreshSummonerMatches;

  public Result execute(int limit) {
    var targets = summonerJpaRepository.findRefreshTargets(limit);

    int processed = 0;
    int rawCreated = 0;

    for (var s : targets) {
      var r = refreshSummonerMatches.execute(s.getPuuid());
      processed++;
      rawCreated += r.rawCreated();
    }

    return new Result(processed, rawCreated);
  }

  public record Result(int processed, int rawCreated) {}
}

