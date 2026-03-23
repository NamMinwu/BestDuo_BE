package com.bestduo_BE.refresh.application;

import com.bestduo_BE.refresh.application.port.LeagueEntriesRefreshLoader;
import com.bestduo_BE.common.application.port.MatchIdsFinder;
import com.bestduo_BE.common.application.port.MatchQueueEnqueuer;
import com.bestduo_BE.refresh.application.port.SummonerRefreshStatusUpdater;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.persistence.entity.Summoner;
import com.bestduo_BE.common.infra.riot.dto.LeagueEntry;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshSummonerMatches {

  private static final int FETCH_COUNT = 50;
  private static final int PRIORITY_REFRESH = 10;

  private final LeagueEntriesRefreshLoader leagueEntriesRefreshLoader;
  private final MatchIdsFinder matchIdsFinder;
  private final MatchQueueEnqueuer matchQueueEnqueuer;

  private final SummonerRefreshStatusUpdater summonerRefreshStatusUpdater;

  /**
   * Phase5(큐 기반 Refresh):
   * - 현재 티어 조회 → Tier 라벨 결정
   * - lastMatchStartTime 이후 matchIds 조회(증분)
   * - match_queue에 enqueue만 수행 (detail 처리는 QueueWorker가 수행)
   */
  public Result execute(String puuid) {
    Summoner s = summonerRefreshStatusUpdater.findOrCreate(puuid);

    try {
      summonerRefreshStatusUpdater.markRefreshRunning(puuid);

      Tier collectionTier = resolveCollectionTierBySolo(puuid);
      if (collectionTier == null || collectionTier == Tier.ALL_TIERS) {
        summonerRefreshStatusUpdater.markRefreshDone(puuid, s.getLastMatchStartTime());
        return new Result(puuid, 0, collectionTier, s.getLastMatchStartTime());
      }

      List<String> matchIds = loadMatchIds(puuid, s.getLastMatchStartTime());

      if (matchIds == null || matchIds.isEmpty()) {
        summonerRefreshStatusUpdater.markRefreshDone(puuid, s.getLastMatchStartTime());
        return new Result(puuid, 0, collectionTier, s.getLastMatchStartTime());
      }

      // ✅ detail 호출 X
      // ✅ matchIds를 queue에 적재 (refresh가 우선순위 높음)
      matchQueueEnqueuer.enqueueAllIdempotent(matchIds, collectionTier, PRIORITY_REFRESH);

      // 커서 갱신 정책:
      // - 정확하게 하려면 "detail 처리 시점"에 matchStartTime을 보고 갱신하는 게 맞음.
      // - 지금은 enqueue 성공을 DONE으로 기록하고, 커서는 그대로 둔다(안전하게 보수적).
      summonerRefreshStatusUpdater.markRefreshDone(puuid, s.getLastMatchStartTime());

      return new Result(puuid, matchIds.size(), collectionTier, s.getLastMatchStartTime());

    } catch (Exception e) {
      log.error("Refresh enqueue failed. puuid={}", puuid, e);
      summonerRefreshStatusUpdater.markRefreshError(puuid);
      throw e;
    }
  }

  private List<String> loadMatchIds(String puuid, Long lastMatchStartTimeSecOrNull) {
    if (lastMatchStartTimeSecOrNull == null) {
      return matchIdsFinder.findRecentMatchIds(puuid, FETCH_COUNT);
    }
    return matchIdsFinder.findMatchIdsSince(puuid, lastMatchStartTimeSecOrNull, FETCH_COUNT);
  }

  /**
   * Riot entries/by-puuid에서 SOLO 티어를 가져와서
   * 서비스 저장 기준(Tier)로 변환한다.
   */
  private Tier resolveCollectionTierBySolo(String puuid) {
    List<LeagueEntry> entries = leagueEntriesRefreshLoader.loadEntriesByPuuid(puuid);

    LeagueEntry solo = entries.stream()
        .filter(e -> "RANKED_SOLO_5x5".equals(e.queueType()))
        .max(Comparator.comparingLong(e -> e.leaguePoints() == null ? 0L : e.leaguePoints()))
        .orElse(null);

    if (solo == null || solo.tier() == null) return null;

    try {
      Tier riotTier = Tier.valueOf(solo.tier()); // e.g. "EMERALD"
      return switch (riotTier) {
        default -> riotTier;
      };
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  public record Result(
      String puuid,
      int matchIdsEnqueued,
      Tier collectionTier,
      Long lastMatchStartTimeSec
  ) {}
}
