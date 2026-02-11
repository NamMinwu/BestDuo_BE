package com.bestduo_BE.application;

import com.bestduo_BE.application.port.MatchIdsFinder;
import com.bestduo_BE.application.port.MatchQueueEnqueuer;
import com.bestduo_BE.application.port.SummonerExpandQueue;
import com.bestduo_BE.domain.model.Tier;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SeedExpansionWorker {

  private static final int PRIORITY_EXPAND = 60;

  private final SummonerExpandQueue summonerExpandQueue;
  private final MatchIdsFinder matchIdsFinder;
  private final MatchQueueEnqueuer matchQueueEnqueuer;

  /**
   * Phase2B(큐 기반):
   * - 확장 대상 puuid를 뽑아
   * - matchIds만 가져와 match_queue에 적재한다.
   * - match detail(Phase1)은 MatchDetailQueueWorker가 수행한다.
   *
   * NOTE:
   * - participants 기반 seed 확장(10 puuid upsert)은
   *   "match detail 처리 시점"에 자동으로 발생하도록 옮기는 게 정석이다.
   */
  public ExpansionResult execute(int batchSize, int matchesPerPuuid, Tier collectionTier) {
    int puuidPicked = 0;
    int matchIdsFetched = 0;
    int matchIdsEnqueued = 0;

    List<String> puuids = summonerExpandQueue.findReadyPuuds(batchSize);
    puuidPicked = puuids.size();

    for (String puuid : puuids) {
      summonerExpandQueue.markExpandRunning(puuid);

      try {
        List<String> matchIds = matchIdsFinder.findRecentMatchIds(puuid, matchesPerPuuid);
        if (matchIds == null || matchIds.isEmpty()) {
          summonerExpandQueue.markExpandDone(puuid);
          continue;
        }

        matchIdsFetched += matchIds.size();

        // ✅ detail 호출 X
        // ✅ matchIds를 match_queue에 적재
        matchQueueEnqueuer.enqueueAllIdempotent(matchIds, collectionTier, PRIORITY_EXPAND);
        matchIdsEnqueued += matchIds.size(); // (시도 수)

        summonerExpandQueue.markExpandDone(puuid);

      } catch (Exception e) {
        summonerExpandQueue.markExpandError(puuid);
      }
    }

    return new ExpansionResult(puuidPicked, matchIdsFetched, matchIdsEnqueued);
  }

  public record ExpansionResult(
      int puuidPicked,
      int matchIdsFetched,
      int matchIdsEnqueued
  ) {}
}
